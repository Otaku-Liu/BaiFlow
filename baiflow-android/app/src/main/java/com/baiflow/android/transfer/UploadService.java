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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import retrofit2.Call;
import retrofit2.Response;

/**
 * 上传服务 — 前台 Service，按**顺序队列**上传文件到服务器并显示进度通知。
 * <p>
 * 文件选择器返回的是 {@code content://} URI（非文件系统路径），必须经 ContentResolver 读取；
 * 队列严格顺序：一个传完才传下一个，通知带队列位置（第 i/N 个）实时显示百分比；
 * 终端任务在队列保留 4s 宽限期供文件列表轮询拾取（完成换真行 / 失败取消移除），随后清理；
 * 队列耗尽后 {@code STOP_FOREGROUND_DETACH} 脱离前台保留最后一条结果通知（不随 stopSelf 消失）。
 */
public class UploadService extends Service {
    private static final String TAG = "UploadService";
    private static final String CHANNEL_ID = "baiflow_upload";
    private static final int NOTIFICATION_ID = 2002;
    /** 终端任务在队列中的保留宽限期（ms）：供文件列表轮询拾取完成/失败/取消结果并刷新 */
    private static final long GRACE_MS = 4000L;

    /** 上传通知「取消」按钮的动作：取消当前上传中项（无则取消队首排队项） */
    public static final String ACTION_CANCEL = "com.baiflow.android.transport.ACTION_CANCEL_UPLOAD";
    /** 文件列表点占位行取消指定任务的动作 */
    public static final String ACTION_CANCEL_TASK = "com.baiflow.android.transport.ACTION_CANCEL_UPLOAD_TASK";
    public static final String EXTRA_TASK_ID = "task_id";

    public static final String EXTRA_STORAGE_ROOT_ID = "storage_root_id";
    public static final String EXTRA_PARENT_ID = "parent_id";
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_VIEW_USER_ID = "view_user_id";
    public static final String EXTRA_FILE_NAME = "file_name";
    /** 隐私文件夹访问令牌（上传到隐私空间内需 X-Privacy-Access-Token，见后端 checkPrivacyAccess） */
    public static final String EXTRA_PRIVACY_TOKEN = "privacy_token";

    /** 任务状态：排队中 / 上传中 / 完成 / 失败 / 取消 */
    public static final String STATE_QUEUED = "QUEUED";
    public static final String STATE_UPLOADING = "UPLOADING";
    public static final String STATE_DONE_OK = "DONE_OK";
    public static final String STATE_FAILED = "FAILED";
    public static final String STATE_CANCELLED = "CANCELLED";

    /** 上传任务：一次上传的完整上下文 + 可变状态；供文件列表渲染占位进度 */
    public static class UploadTask {
        /** 入队序号（进程内单调递增，用于通知「第 i/N 个」定位） */
        public final int seq;
        public final String taskId;
        public final String rootId;
        public final String parentId;
        public final String fileName;
        public final String viewUserId;
        public final String filePath;
        public final String privacyToken;
        public volatile String state = STATE_QUEUED;
        public volatile int percent;
        /** 上传成功时服务端返回的完整文件信息（文件列表据此原位换真行） */
        public volatile FileItem completedFileItem;
        /** 失败原因（用户可读） */
        public volatile String errorMessage;
        /** 用户请求取消（上传中取消请求 / 排队跳过标记） */
        public volatile boolean cancelRequested;

        UploadTask(int seq, String taskId, String rootId, String parentId, String fileName,
                   String viewUserId, String filePath, String privacyToken) {
            this.seq = seq;
            this.taskId = taskId;
            this.rootId = rootId;
            this.parentId = parentId;
            this.fileName = fileName;
            this.viewUserId = viewUserId;
            this.filePath = filePath;
            this.privacyToken = privacyToken;
        }
    }

    /** 上传任务队列（严格顺序，worker 单线程消费） */
    private static final ConcurrentLinkedQueue<UploadTask> queue = new ConcurrentLinkedQueue<>();
    /** 任务序号（进程内唯一，生成 taskId 与 seq） */
    private static final AtomicInteger taskSeq = new AtomicInteger();
    /** 当前「波次」起始序号与入队总数（队列清空后首个入队任务开启新波次；通知「第 i/N 个」用） */
    private static volatile int waveStartSeq;
    private static volatile int waveCount;
    /** worker 是否在跑（进程级单 worker 守卫，防止跨 Service 实例重复消费队列） */
    private static final AtomicBoolean workerRunning = new AtomicBoolean(false);

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private NotificationManager notificationManager;
    private SessionManager session;
    /** 当前正在执行的上传请求（供「取消」动作中断）；volatile：取消可能来自通知按钮/占位行触发的另一线程 */
    private volatile retrofit2.Call<?> currentCall;
    /** 当前上传中任务（通知「取消」针对它） */
    private volatile UploadTask currentUploading;
    /** 本次 Service 是否已 startForeground（重启后重置） */
    private boolean foregroundStarted;

    /** 队列快照（供文件列表渲染占位）；无则空列表 */
    public static List<UploadTask> getQueueSnapshot() {
        return new ArrayList<>(queue);
    }

    /** 从队列移除指定任务（文件列表处理完终端结果后调用，避免队列积压） */
    public static void removeTask(String taskId) {
        queue.removeIf(t -> t.taskId.equals(taskId));
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

        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            // 上传通知上的「取消」：取消当前上传中项；无则取消队首排队项
            cancelTask(currentUploading != null ? currentUploading : firstQueued());
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL_TASK.equals(action)) {
            // 文件列表点占位行取消指定任务（当前项中断请求；排队项直接标记取消）
            cancelTask(findTask(intent.getStringExtra(EXTRA_TASK_ID)));
            return START_NOT_STICKY;
        }

        // 新上传任务入队
        String storageRootId = intent.getStringExtra(EXTRA_STORAGE_ROOT_ID);
        String parentId = intent.getStringExtra(EXTRA_PARENT_ID);
        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
        String viewUserId = intent.getStringExtra(EXTRA_VIEW_USER_ID);
        String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
        String privacyToken = intent.getStringExtra(EXTRA_PRIVACY_TOKEN);
        if (fileName == null || fileName.isEmpty()) {
            // 兜底：选择器没传名字时退化为路径末尾名（content:// 下不可用，此时应总是传 file_name）
            fileName = filePath != null ? new File(filePath).getName() : "file";
        }
        int seq = taskSeq.incrementAndGet();
        // 波次按「无存活任务」判定：终端任务在宽限期内仍留在队列，queue.isEmpty() 会误判不重置
        if (firstQueued() == null && currentUploading == null) {
            waveStartSeq = seq;
            waveCount = 0;
        }
        waveCount++;
        UploadTask task = new UploadTask(seq, "up-" + seq,
                storageRootId, parentId, fileName, viewUserId, filePath, privacyToken);
        queue.add(task);

        // 首个任务启动前台；之后若当前无上传中任务（如刚停止前台后又入队）需重新进入前台
        if (!foregroundStarted || currentUploading == null) {
            startForeground(NOTIFICATION_ID,
                    buildNotification(task.fileName, getString(R.string.transfer_upload_preparing), true, true, 0, null));
            foregroundStarted = true;
        }
        ensureWorker();
        return START_NOT_STICKY;
    }

    /**
     * 取消任务：上传中 → 中断请求（结果由上传线程检测后统一收尾）；排队中 → 标记取消由 worker 跳过。
     */
    private void cancelTask(UploadTask task) {
        if (task == null) {
            return;
        }
        if (STATE_UPLOADING.equals(task.state)) {
            task.cancelRequested = true;
            retrofit2.Call<?> c = currentCall;
            if (c != null && !c.isCanceled()) {
                c.cancel();
            }
        } else if (STATE_QUEUED.equals(task.state)) {
            task.state = STATE_CANCELLED;
            // 同时标记取消请求：worker 已把该任务从队列取走（firstQueued 返回后）时，
            // 后续置 UPLOADING 也不会覆盖——上传线程在读完文件后检测 cancelRequested 收尾
            task.cancelRequested = true;
            mainHandler.postDelayed(() -> queue.remove(task), GRACE_MS);
            if (firstQueued() == null && currentUploading == null) {
                // 唯一排队项被取消：通知直接显示「已取消」（无后续任务覆盖）
                notificationManager.notify(NOTIFICATION_ID,
                        buildNotification(task.fileName, getString(R.string.transfer_upload_cancelled),
                                false, false, -1, null));
            }
        }
    }

    /** 进程级单 worker：串行消费队列；无排队任务时退出并调度宽限期后的停止 */
    private void ensureWorker() {
        if (!workerRunning.compareAndSet(false, true)) {
            return;
        }
        new Thread(() -> {
            try {
                while (true) {
                    UploadTask task = firstQueued();
                    if (task == null) {
                        break;
                    }
                    uploadTask(task);
                }
            } finally {
                workerRunning.set(false);
                // 释放 worker 标志后补查一次：worker 退出与新任务入队存在竞态，期间 ensureWorker 的
                // CAS 可能失败而漏启动；有排队任务则补启动，否则走宽限期收尾
                if (firstQueued() != null) {
                    ensureWorker();
                } else {
                    scheduleIdleCleanup();
                }
            }
        }).start();
    }

    private void uploadTask(UploadTask task) {
        task.state = STATE_UPLOADING;
        currentUploading = task;
        int[] pos = livePosition(task);
        String queuePos = getString(R.string.transfer_upload_queue_pos, pos[0], pos[1], task.fileName);
        updateNotification(task.fileName, getString(R.string.transfer_upload_preparing), true, true, 0, queuePos);

        try {
            // 竞态兜底：worker 取出排队任务瞬间被取消，先于读文件检测，避免大文件白读 content URI
            if (task.cancelRequested) {
                Log.i(TAG, "上传已取消: " + task.fileName);
                finishTerminal(task, STATE_CANCELLED, getString(R.string.transfer_upload_cancelled));
                return;
            }
            byte[] fileBytes = readFileBytes(task.filePath);
            if (task.cancelRequested) {
                Log.i(TAG, "上传已取消: " + task.fileName);
                finishTerminal(task, STATE_CANCELLED, getString(R.string.transfer_upload_cancelled));
                return;
            }

            ApiClient client = ApiClient.getInstance(session);
            final int[] lastPercent = {0};
            IntConsumer progress = percent -> {
                task.percent = percent;
                if (percent != lastPercent[0]) {
                    lastPercent[0] = percent;
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(task.fileName,
                            getString(R.string.transfer_upload_progress, percent), true, false, percent, queuePos));
                }
            };
            retrofit2.Call<ApiResponse<FileItem>> call = client.uploadFile(task.rootId, task.parentId,
                    fileBytes, task.fileName, task.viewUserId, task.privacyToken, progress);
            currentCall = call;

            updateNotification(task.fileName, getString(R.string.transfer_upload_uploading), true, true, 0, queuePos);
            retrofit2.Response<ApiResponse<FileItem>> response = call.execute();

            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                // 服务端已存盘：即使刚请求取消也按完成处理（文件确实已上传，避免误报「已取消」且不刷新）
                Log.i(TAG, "上传完成: " + task.fileName);
                task.completedFileItem = response.body().getData();
                finishTerminal(task, STATE_DONE_OK, getString(R.string.transfer_upload_completed));
            } else if (task.cancelRequested) {
                Log.i(TAG, "上传已取消: " + task.fileName);
                finishTerminal(task, STATE_CANCELLED, getString(R.string.transfer_upload_cancelled));
            } else {
                String msg = response.body() != null ? response.body().getMessage()
                        : getString(R.string.transfer_upload_failed);
                Log.w(TAG, "上传失败: " + task.fileName + " -> " + msg);
                task.errorMessage = getString(R.string.transfer_upload_failed_detail, msg);
                finishTerminal(task, STATE_FAILED, task.errorMessage);
            }

        } catch (Exception e) {
            if (task.cancelRequested) {
                Log.i(TAG, "上传已取消: " + task.fileName);
                finishTerminal(task, STATE_CANCELLED, getString(R.string.transfer_upload_cancelled));
            } else {
                Log.e(TAG, "上传失败", e);
                task.errorMessage = getString(R.string.transfer_upload_failed_detail,
                        e.getMessage() != null ? e.getMessage() : "");
                finishTerminal(task, STATE_FAILED, task.errorMessage);
            }
        } finally {
            currentCall = null;
            currentUploading = null;
        }
    }

    /**
     * 标记任务终端状态并调度宽限期后清理。仅当队列已无后续上传任务时展示结果通知
     * （有后续时由下一个任务的通知覆盖，避免瞬时闪烁）。
     */
    private void finishTerminal(UploadTask task, String state, String resultText) {
        task.state = state;
        if (firstQueued() == null) {
            notificationManager.notify(NOTIFICATION_ID,
                    buildNotification(task.fileName, resultText, false, false, -1, null));
        }
        mainHandler.postDelayed(() -> queue.remove(task), GRACE_MS);
    }

    /** 队列耗尽后的收尾：宽限期内容许新的排队任务续上（此时不停止）；否则停止服务，结果通知保留 */
    private void scheduleIdleCleanup() {
        mainHandler.postDelayed(() -> {
            if (hasLiveTask()) {
                return;
            }
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
        }, GRACE_MS);
    }

    /** 下一个待上传任务（仅 QUEUED 未开始；上传中的任务由 currentUploading 持有，不在此列） */
    private UploadTask firstQueued() {
        for (UploadTask t : queue) {
            if (STATE_QUEUED.equals(t.state)) {
                return t;
            }
        }
        return null;
    }

    /** 是否存在存活任务（排队中或上传中）；宽限期停止判断用，防止误停正在上传的任务 */
    private boolean hasLiveTask() {
        return currentUploading != null || firstQueued() != null;
    }

    private UploadTask findTask(String taskId) {
        if (taskId == null) {
            return null;
        }
        for (UploadTask t : queue) {
            if (taskId.equals(t.taskId)) {
                return t;
            }
        }
        return null;
    }

    /** 任务在当前波次中的位置 {序号, 总数}（入队即定、不随完成缩水；供通知「第 i/N 个」） */
    private int[] livePosition(UploadTask task) {
        return new int[]{task.seq - waveStartSeq + 1, waveCount};
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

    /**
     * 构建上传通知。queuePos 非空时正文为「第 i/N 个 fileName 状态」（队列上传）；
     * 为空时正文为「fileName - 状态」（准备/结果态）。
     */
    private Notification buildNotification(String fileName, String status, boolean ongoing,
                                           boolean indeterminate, int percent, String queuePos) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String content = queuePos != null ? queuePos + " " + status : fileName + " - " + status;
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.transfer_upload_notification_title))
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(pending)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true);
        if (ongoing) {
            // 上传中通知提供「取消」动作（取消当前上传中项，不做暂停/续传）
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
                                    boolean indeterminate, int percent, String queuePos) {
        notificationManager.notify(NOTIFICATION_ID,
                buildNotification(fileName, status, ongoing, indeterminate, percent, queuePos));
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
