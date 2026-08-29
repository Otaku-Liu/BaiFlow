package com.baiflow.android.sync;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.data.SyncService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 笔记实时同步 SSE 客户端 — 在线模式下长连 {@code /api/events}，收到 {@code NOTE_UPDATED} 立即触发一次增量同步。
 * <p>
 * - 手写 SSE 解析（无第三方依赖）：{@code event:/data:} 行 + 空行分发；忽略心跳注释行；
 * - 生命周期跟随 MainActivity（在线 start / 离线/登出 stop，见 MainActivity）；
 * - 断线指数退避重连（上限 60s），与 WorkManager 周期同步并存：长连接保证及时，周期任务兜底。
 */
public final class NoteSseClient {

    private static final String TAG = "NoteSse";
    private static final long MAX_RETRY_MS = 60_000L;

    private static NoteSseClient instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private OkHttpClient httpClient;
    private Call currentCall;
    private int retryCount;

    /** 同步完成回调（主线程触发），供当前可见的笔记列表刷新 */
    public interface OnSyncCompleteListener {
        void onSyncComplete();
    }

    /** 主线程写、同步工作线程读，需 volatile 保证可见性 */
    private static volatile OnSyncCompleteListener syncListener;

    public static void setSyncListener(OnSyncCompleteListener listener) {
        syncListener = listener;
    }

    public static void clearSyncListener() {
        syncListener = null;
    }

    private NoteSseClient() {
    }

    public static synchronized NoteSseClient getInstance() {
        if (instance == null) {
            instance = new NoteSseClient();
        }
        return instance;
    }

    /** 在线模式进入时启动长连接；重复调用幂等 */
    public synchronized void start(Context context) {
        if (running) {
            return;
        }
        SessionManager session = SessionManager.getInstance(context);
        String token = session.getToken();
        if (token == null || token.isEmpty()) {
            return;
        }
        running = true;
        retryCount = 0;
        connect(context);
    }

    /** 停止长连接并取消重连；登出/离线/页面销毁时调用 */
    public synchronized void stop() {
        running = false;
        mainHandler.removeCallbacksAndMessages(null);
        if (currentCall != null) {
            currentCall.cancel();
            currentCall = null;
        }
    }

    private void connect(Context context) {
        SessionManager session = SessionManager.getInstance(context);
        String token = session.getToken();
        if (!running || token == null || token.isEmpty()) {
            return;
        }
        String url = session.getServerUrl() + "/api/events?token=" + Uri.encode(token);
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .build();
        if (httpClient == null) {
            // 长连接：不设读超时（心跳注释行保活），连接超时保持默认
            httpClient = new OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .build();
        }
        currentCall = httpClient.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                scheduleReconnect(context);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!running) {
                        return;
                    }
                    if (response.code() == 401) {
                        // 会话被吊销：与 AuthInterceptor 的 401 语义一致，踢回登录且不再重连
                        response.close();
                        running = false;
                        SessionManager.getInstance(context).kickToLogin();
                        return;
                    }
                    if (!response.isSuccessful()) {
                        response.close();
                        scheduleReconnect(context);
                        return;
                    }
                    Reader r = response.body() != null ? response.body().charStream() : null;
                    if (r == null) {
                        response.close();
                        scheduleReconnect(context);
                        return;
                    }
                    handleStream(context, r);
                } finally {
                    if (response.body() != null) {
                        response.close();
                    }
                    // 流读完（连接断开）→ 重连
                    scheduleReconnect(context);
                }
            }
        });
    }

    /** 阻塞读 SSE 流：event:/data: 行按空行分发；心跳注释行（以 : 开头）忽略 */
    private void handleStream(Context context, Reader reader) {
        BufferedReader br = new BufferedReader(reader);
        String eventName = null;
        StringBuilder data = new StringBuilder();
        try {
            String line;
            while (running && (line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    if (eventName != null && data.length() > 0) {
                        dispatch(context, eventName, data.toString());
                    }
                    eventName = null;
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring("data:".length()).trim());
                }
                // 其它行（": 心跳注释" 等）忽略
            }
        } catch (IOException e) {
            Log.d(TAG, "SSE 读取断开: " + e.getMessage());
        }
    }

    /** 分发事件：NOTE_UPDATED → 后台触发一次增量同步，完成后通知可见列表刷新 */
    private void dispatch(Context context, String event, String payload) {
        if (!"NOTE_UPDATED".equals(event)) {
            return;
        }
        if (!SessionManager.getInstance(context).isOnlineMode()) {
            return;
        }
        Log.d(TAG, "NOTE_UPDATED -> syncOnce, payload=" + payload);
        new Thread(() -> {
            SyncService.syncOnce(context);
            OnSyncCompleteListener listener = syncListener;
            if (listener != null) {
                mainHandler.post(listener::onSyncComplete);
            }
        }).start();
    }

    /** 断线重连：指数退避（1s → 2s → … 封顶 60s）；stop 后不再重连 */
    private void scheduleReconnect(Context context) {
        if (!running) {
            return;
        }
        long delay = Math.min((1L << Math.min(retryCount, 8)) * 1000L, MAX_RETRY_MS);
        retryCount++;
        mainHandler.postDelayed(() -> connect(context), delay);
    }
}
