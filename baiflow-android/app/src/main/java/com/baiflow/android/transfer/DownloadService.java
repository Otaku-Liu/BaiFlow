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
import java.io.FileOutputStream;
import java.io.InputStream;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * 下载服务 — 前台 Service，下载文件并显示进度通知。
 */
public class DownloadService extends Service {
    private static final String TAG = "DownloadService";
    private static final String CHANNEL_ID = "baiflow_download";
    private static final int NOTIFICATION_ID = 2001;

    public static final String EXTRA_FILE_ID = "file_id";
    public static final String EXTRA_FILE_NAME = "file_name";
    public static final String EXTRA_SIZE_BYTES = "size_bytes";

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

        String fileId = intent.getStringExtra(EXTRA_FILE_ID);
        String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
        long totalBytes = intent.getLongExtra(EXTRA_SIZE_BYTES, 0);

        startForeground(NOTIFICATION_ID, buildNotification(fileName, "准备下载...", 0));

        // 在后台线程执行下载
        new Thread(() -> performDownload(fileId, fileName, totalBytes)).start();

        return START_NOT_STICKY;
    }

    private void performDownload(String fileId, String fileName, long totalBytes) {
        try {
            ApiClient client = ApiClient.getInstance(session);
            Call<ResponseBody> call = client.downloadFile(fileId, null);
            Response<ResponseBody> response = call.execute();

            if (!response.isSuccessful() || response.body() == null) {
                updateNotification(fileName, "下载失败：服务器错误", 0);
                stopSelf();
                return;
            }

            // 保存到 app 私有下载目录
            File downloadDir = new File(getFilesDir(), "downloads");
            if (!downloadDir.exists()) { downloadDir.mkdirs(); }
            File outputFile = new File(downloadDir, fileName);

            InputStream in = response.body().byteStream();
            FileOutputStream out = new FileOutputStream(outputFile);

            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int bytesRead;
            long lastUpdate = System.currentTimeMillis();

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;

                // 每 500ms 更新一次通知
                long now = System.currentTimeMillis();
                if (now - lastUpdate >= 500) {
                    int progress = totalBytes > 0 ? (int) (downloaded * 100 / totalBytes) : 0;
                    updateNotification(fileName,
                            formatSize(downloaded) + " / " + (totalBytes > 0 ? formatSize(totalBytes) : "未知"),
                            Math.min(progress, 100));
                    lastUpdate = now;
                }
            }

            out.close();
            in.close();

            updateNotification(fileName, "下载完成", 100);
            Log.i(TAG, "下载完成: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "下载失败", e);
            updateNotification(fileName, "下载失败：" + e.getMessage(), 0);
        }
        stopSelf();
    }

    private Notification buildNotification(String fileName, String status, int progress) {
        Intent intent = new Intent(this, FileListActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BaiFlow 下载")
                .setContentText(fileName + " - " + status)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(pending)
                .setOngoing(true)
                .setProgress(100, progress, progress == 0)
                .build();
    }

    private void updateNotification(String fileName, String status, int progress) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(fileName, status, progress));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "下载通知", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("BaiFlow 文件下载进度");
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private String formatSize(long bytes) {
        if (bytes < 1024) { return bytes + " B"; }
        if (bytes < 1024 * 1024) { return String.format("%.1f KB", bytes / 1024.0); }
        if (bytes < 1024 * 1024 * 1024) { return String.format("%.1f MB", bytes / (1024.0 * 1024)); }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
