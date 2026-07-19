package com.baiflow.android.transfer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.FileItem;
import com.baiflow.android.ui.FileListActivity;
import java.io.File;
import java.io.FileInputStream;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * 上传服务 — 前台 Service，上传文件到服务器并显示进度通知。
 */
public class UploadService extends Service {
    private static final String TAG = "UploadService";
    private static final String CHANNEL_ID = "baiflow_upload";
    private static final int NOTIFICATION_ID = 2002;

    public static final String EXTRA_STORAGE_ROOT_ID = "storage_root_id";
    public static final String EXTRA_PARENT_ID = "parent_id";
    public static final String EXTRA_FILE_PATH = "file_path";

    private NotificationManager notificationManager;
    private SessionManager session;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        session = SessionManager.getInstance(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String storageRootId = intent.getStringExtra(EXTRA_STORAGE_ROOT_ID);
        String parentId = intent.getStringExtra(EXTRA_PARENT_ID);
        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);

        File file = new File(filePath);
        String fileName = file.getName();

        startForeground(NOTIFICATION_ID, buildNotification(fileName, "准备上传...", false));

        new Thread(() -> performUpload(storageRootId, parentId, file)).start();

        return START_NOT_STICKY;
    }

    private void performUpload(String storageRootId, String parentId, File file) {
        try {
            String fileName = file.getName();
            byte[] fileBytes = readFileBytes(file);

            ApiClient client = ApiClient.getInstance(session);
            Call<ApiResponse<FileItem>> call = client.uploadFile(storageRootId, parentId,
                    fileBytes, fileName, null);
            updateNotification(fileName, "上传中...", true);
            Response<ApiResponse<FileItem>> response = call.execute();

            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                updateNotification(fileName, "上传完成", false);
                Log.i(TAG, "上传完成: " + fileName);
            } else {
                String msg = response.body() != null ? response.body().getMessage() : "上传失败";
                updateNotification(fileName, "上传失败：" + msg, false);
            }

        } catch (Exception e) {
            Log.e(TAG, "上传失败", e);
            updateNotification(file.getName(), "上传失败：" + e.getMessage(), false);
        }
        stopSelf();
    }

    private byte[] readFileBytes(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        in.read(data);
        in.close();
        return data;
    }

    private Notification buildNotification(String fileName, String status, boolean ongoing) {
        Intent intent = new Intent(this, FileListActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BaiFlow 上传")
                .setContentText(fileName + " - " + status)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(pending)
                .setOngoing(ongoing)
                .build();
    }

    private void updateNotification(String fileName, String status, boolean ongoing) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(fileName, status, ongoing));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "上传通知", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("BaiFlow 文件上传进度");
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
