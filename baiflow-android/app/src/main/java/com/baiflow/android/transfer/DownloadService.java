package com.baiflow.android.transfer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.FileItem;
import com.baiflow.android.ui.activity.MainActivity;
import com.baiflow.android.util.DownloadLocationStore;
import com.baiflow.android.util.FormatUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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

        startForeground(NOTIFICATION_ID, buildNotification(fileName, getString(R.string.transfer_download_preparing), 0));

        // 在后台线程执行下载
        new Thread(() -> performDownload(fileId, fileName, totalBytes)).start();

        return START_NOT_STICKY;
    }

    private void performDownload(String fileId, String fileName, long totalBytes) {
        Uri mediaUri = null;   // API 29+ 的 MediaStore 条目（失败时清理）
        File targetFile = null; // API 26-28 落盘文件（持久化保存位置用）
        try {
            ApiClient client = ApiClient.getInstance(session);
            Call<ResponseBody> call = client.downloadFile(fileId, null);
            Response<ResponseBody> response = call.execute();

            if (!response.isSuccessful() || response.body() == null) {
                updateNotification(fileName, getString(R.string.transfer_download_server_error), 0);
                stopSelf();
                return;
            }

            String safeName = sanitizeFileName(fileName);
            InputStream in = response.body().byteStream();
            OutputStream out;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+：写入公共 Download（MediaStore，作用域存储，无需权限）
                String mime = java.net.URLConnection.guessContentTypeFromName(safeName);
                if (mime == null) { mime = "application/octet-stream"; }
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                values.put(MediaStore.Downloads.MIME_TYPE, mime);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                mediaUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (mediaUri == null) {
                    updateNotification(fileName, getString(R.string.transfer_download_failed_detail, "无法创建下载文件"), 0);
                    stopSelf();
                    return;
                }
                out = getContentResolver().openOutputStream(mediaUri);
            } else {
                // API 26-28：写入公共 Download 目录（需 WRITE_EXTERNAL_STORAGE，由调用方申请）
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) { dir.mkdirs(); }
                targetFile = new File(dir, safeName);
                out = new FileOutputStream(targetFile);
            }

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
                            FormatUtil.formatSize(downloaded) + " / " + (totalBytes > 0 ? FormatUtil.formatSize(totalBytes) : getString(R.string.transfer_unknown_size)),
                            Math.min(progress, 100));
                    lastUpdate = now;
                }
            }

            out.flush();
            out.close();
            in.close();

            // MediaStore 收尾：标记可见（文件管理器/下载应用即可看到）
            if (mediaUri != null) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(mediaUri, done, null, null);
            }

            // 持久化保存位置（供下载记录详情「打开文件/保存位置」；仅本机下载过才可打开）
            DownloadLocationStore.save(this, fileId, safeName,
                    mediaUri != null ? mediaUri.toString() : null,
                    targetFile != null ? targetFile.getAbsolutePath() : null);

            updateNotification(fileName, getString(R.string.transfer_download_completed), 100);
            Log.i(TAG, "下载完成: " + safeName);

        } catch (Exception e) {
            Log.e(TAG, "下载失败", e);
            // MediaStore 半成品清理，避免残留 IS_PENDING=1 的隐藏条目
            if (mediaUri != null) {
                try {
                    getContentResolver().delete(mediaUri, null, null);
                } catch (Exception ignored) {
                }
            }
            updateNotification(fileName, getString(R.string.transfer_download_failed_detail, e.getMessage()), 0);
        }
        stopSelf();
    }

    /** 文件名消毒：去除路径分隔符与非法字符（含控制字符），保留中文与空格 */
    private String sanitizeFileName(String name) {
        if (name == null) { return "file"; }
        return name.replaceAll("[/\\\\:*?\"<>|\\p{Cntrl}]", "_");
    }

    private Notification buildNotification(String fileName, String status, int progress) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.transfer_download_notification_title))
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
                    CHANNEL_ID, getString(R.string.transfer_download_channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.transfer_download_channel_desc));
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

}
