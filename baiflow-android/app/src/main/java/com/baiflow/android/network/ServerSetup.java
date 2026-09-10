package com.baiflow.android.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 服务器探测 —— 判断候选地址「能否连通」与「是否已完成首次初始化」。
 * <p>
 * 探测走独立的 OkHttpClient，不经过 {@link ApiClient}：目标可能是尚未保存的地址，
 * 也可能是在当前地址连不上时需要重新确认的地址。回调统一切回主线程。
 * <p>
 * 只负责网络探测；「未初始化」的界面呈现（提示框、跳浏览器）见
 * {@link com.baiflow.android.util.ServerSetupUi}。
 */
public final class ServerSetup {

    /** 探测结果回调（主线程） */
    public interface ProbeCallback {
        /**
         * @param reachable   健康检查通过
         * @param initialized 服务器已完成首次初始化（未可达时恒为 true，不参与判断）
         * @param detail      失败原因文案（可达时为 null）
         */
        void onResult(boolean reachable, boolean initialized, String detail);
    }

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ServerSetup() {}

    /**
     * 探测地址：先打公开的 {@code /api/health}，可达再查 {@code /api/setup/status}
     * 判断该服务器是否已完成首次初始化。
     */
    public static void probe(final Context context, final String baseUrl, final ProbeCallback callback) {
        new Thread(() -> {
            boolean reachable = false;
            boolean initialized = true;
            String detail = null;
            try {
                try (Response health = CLIENT.newCall(new Request.Builder()
                        .url(baseUrl + "/api/health").build()).execute()) {
                    if (health.isSuccessful()) {
                        reachable = true;
                    } else {
                        detail = "HTTP " + health.code();
                    }
                }
                if (reachable) {
                    try (Response status = CLIENT.newCall(new Request.Builder()
                            .url(baseUrl + "/api/setup/status").build()).execute()) {
                        if (status.isSuccessful() && status.body() != null) {
                            initialized = parseInitialized(status.body().string());
                        }
                    }
                }
            } catch (IOException e) {
                detail = context.getString(NetworkFeedback.classify(context));
            } catch (RuntimeException e) {
                // 地址非法（OkHttp 解析失败）等
                detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }

            final boolean fReachable = reachable;
            final boolean fInitialized = initialized;
            final String fDetail = detail;
            MAIN.post(() -> callback.onResult(fReachable, fInitialized, fDetail));
        }).start();
    }

    /** 从 /api/setup/status 响应里取 data.initialized；解析失败按已初始化处理（不误报未初始化） */
    private static boolean parseInitialized(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                JsonObject data = root.getAsJsonObject("data");
                if (data.has("initialized")) {
                    return data.get("initialized").getAsBoolean();
                }
            }
        } catch (RuntimeException ignored) {
            // 落到默认值
        }
        return true;
    }
}
