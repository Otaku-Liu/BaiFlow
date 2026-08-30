package com.baiflow.android.transfer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.FileItem;
import com.baiflow.android.ui.activity.MainActivity;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.function.IntConsumer;
import retrofit2.Call;
import retrofit2.Response;

/**
 * 上传服务 — 前台 Service，上传文件到服务器并显示进度通知。
 * <p>
 * 文件选择器返回的是 {@code content://} URI（非文件系统路径），必须经 ContentResolver 读取；
 * 进度走 {@link ApiClient#uploadFile} 的回调，通知带进度条实时显示百分比；
 * 完成/失败仍更新同一条通知，用 {@code STOP_FOREGROUND_DETACH} 脱离前台保留结果（不随 stopSelf 消失）。
 */
public class UploadService extends Service {
    private static final String TAG = "UploadService";
    private static final String CHANNEL_ID = "baiflow_upload";
    private static final int NOTIFICATION_ID = 2002;
    /** 上传通知「取消」按钮的动作 */
    public static final String ACTION_CANCEL = "com.baiflow.android.transport.ACTION_CANCEL_UPLOAD";

    public static final String EXTRA_STORAGE_ROOT_ID = "storage_root_id";
    public static final String EXTRA_PARENT_ID = "parent_id";
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_VIEW_USER_ID = "view_user_id";
    public static final String EXTRA_FILE_NAME = "file_name";

    private NotificationManager notificationManager;
    private SessionManager session;
    /** 当前上传请求（供「取消」动作中断）；volatile：取消可能来自通知按钮触发的另一个线程 */
    private volatile retrofit2.Call<?> currentCall;
    private volatile boolean cancelled;

    /** 当前进行中的上传任务（单上传前台服务；供文件列表渲染占位进度） */
    public static class UploadTask {
        public final String rootId;
        public final String parentId;
        public final String fileName;
        public volatile int percent;
        public volatile boolean finished;   // 完成/失败/取消后置 true（文件列表据此刷新并移除占位）

        UploadTask(String rootId, String parentId, String fileName) {
            this.rootId = rootId;
            this.parentId = parentId;
            this.fileName = fileName;
        }
    }

    private static volatile UploadTask currentTask;

    /** 当前上传任务（无则 null）；文件列表据此渲染占位进度 */
    public static UploadTask getCurrentTask() {
        return currentTask;
    }

    /** 清空当前任务（文件列表完成刷新后调用，避免旧任务残留） */
    public static void clearTask() {
        currentTask = null;
    }

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

        // 上传通知上的「取消」按钮：标记取消并中断当前请求，结果由上传线程检测后统一收尾
        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelled = true;
            retrofit2.Call<?> c = currentCall;
            if (c != null && !c.isCanceled()) {
                c.cancel();
            }
            return START_NOT_STICKY;
        }

        cancelled = false;
        String storageRootId = intent.getStringExtra(EXTRA_STORAGE_ROOT_ID);
        String parentId = intent.getStringExtra(EXTRA_PARENT_ID);
        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
        String viewUserId = intent.getStringExtra(EXTRA_VIEW_USER_ID);
        String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
        if (fileName == null || fileName.isEmpty()) {
            // 兜底：选择器没传名字时退化为路径末尾名（content:// 下不可用，此时应总是传 file_name）
            fileName = filePath != null ? new File(filePath).getName() : "file";
        }
        final String uploadName = fileName;
        currentTask = new UploadTask(storageRootId, parentId, uploadName);

        startForeground(NOTIFICATION_ID,
                buildNotification(uploadName, getString(R.string.transfer_upload_preparing), true, true, 0));

        new Thread(() -> performUpload(storageRootId, parentId, viewUserId, filePath, uploadName)).start();

        return START_NOT_STICKY;
    }

    private void performUpload(String storageRootId, String parentId, String viewUserId,
                               String filePath, String fileName) {
        try {
            byte[] fileBytes = readFileBytes(filePath);
            if (cancelled) {
                finishWithResult(fileName, getString(R.string.transfer_upload_cancelled));
                return;
            }

            ApiClient client = ApiClient.getInstance(session);
            final int[] lastPercent = {0};
            IntConsumer progress = percent -> {
                UploadTask t = currentTask;
                if (t != null) t.percent = percent;
                if (percent != lastPercent[0]) {
                    lastPercent[0] = percent;
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(fileName,
                            getString(R.string.transfer_upload_progress, percent), true, false, percent));
                }
            };
            retrofit2.Call<ApiResponse<FileItem>> call = client.uploadFile(storageRootId, parentId,
                    fileBytes, fileName, viewUserId, null, progress);
            currentCall = call;

            updateNotification(fileName, getString(R.string.transfer_upload_uploading), true, true, 0);
            retrofit2.Response<ApiResponse<FileItem>> response = call.execute();

            if (cancelled) {
                Log.i(TAG, "上传已取消: " + fileName);
                finishWithResult(fileName, getString(R.string.transfer_upload_cancelled));
            } else if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                Log.i(TAG, "上传完成: " + fileName);
                finishWithResult(fileName, getString(R.string.transfer_upload_completed));
            } else {
                String msg = response.body() != null ? response.body().getMessage()
                        : getString(R.string.transfer_upload_failed);
                Log.w(TAG, "上传失败: " + fileName + " -> " + msg);
                finishWithResult(fileName, getString(R.string.transfer_upload_failed_detail, msg));
            }

        } catch (Exception e) {
            if (cancelled) {
                Log.i(TAG, "上传已取消: " + fileName);
                finishWithResult(fileName, getString(R.string.transfer_upload_cancelled));
            } else {
                Log.e(TAG, "上传失败", e);
                finishWithResult(fileName, getString(R.string.transfer_upload_failed_detail, e.getMessage()));
            }
        } finally {
            currentCall = null;
        }
    }

    /** 展示结果并停服务：STOP_FOREGROUND_DETACH 保留这条通知（否则前台通知随 stopSelf 被移除） */
    private void finishWithResult(String fileName, String resultText) {
        UploadTask t = currentTask;
        if (t != null) t.finished = true;
        notificationManager.notify(NOTIFICATION_ID,
                buildNotification(fileName, resultText, false, false, -1));
        stopForeground(STOP_FOREGROUND_DETACH);
        stopSelf();
        // 短暂保留任务供文件列表检测完成并刷新；随后自动清理，避免残留
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (currentTask == t) currentTask = null;
        }, 4000);
    }

    /**
     * 读取上传内容：支持 {@code content://} / {@code file://} URI（文件选择器返回）与普通文件路径。
     * 完整读入（循环读），避免单次 read 截断大文件。
     */
    private byte[] readFileBytes(String path) throws Exception {
        InputStream in;
        if (path != null && (path.startsWith("content://") || path.startsWith("file://"))) {
            in = getContentResolver().openInputStream(Uri.parse(path));
            if (in == null) {
                throw new java.io.FileNotFoundException("无法打开: " + path);
            }
        } else {
            in = new FileInputStream(new File(path));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try (InputStream src = in) {
            while ((n = src.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
        return out.toByteArray();
    }

    private Notification buildNotification(String fileName, String status, boolean ongoing,
                                           boolean indeterminate, int percent) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.transfer_upload_notification_title))
                .setContentText(fileName + " - " + status)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(pending)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true);
        if (ongoing) {
            // 上传中通知提供「取消」动作（取消中断请求，不做暂停/续传）
            Intent cancelIntent = new Intent(this, UploadService.class);
            cancelIntent.setAction(ACTION_CANCEL);
            PendingIntent cancelPending = PendingIntent.getService(this, 2, cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.transfer_upload_cancel), cancelPending));
        }
        if (indeterminate) {
            b.setProgress(0, 0, true);
        } else if (percent >= 0) {
            b.setProgress(100, percent, false);
        } else {
            b.setProgress(0, 0, false);   // 结果态清除进度条
        }
        return b.build();
    }

    private void updateNotification(String fileName, String status, boolean ongoing,
                                    boolean indeterminate, int percent) {
        notificationManager.notify(NOTIFICATION_ID,
                buildNotification(fileName, status, ongoing, indeterminate, percent));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.transfer_upload_channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.transfer_upload_channel_desc));
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
