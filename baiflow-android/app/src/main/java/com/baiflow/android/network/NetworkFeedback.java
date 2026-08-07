package com.baiflow.android.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.baiflow.android.R;

/**
 * 网络连接失败统一提示（去重 + 分类）。
 * <p>
 * - 断连时段（offline）内只提示一次，任一次 HTTP 响应即视为恢复并提示「网络已恢复」；
 * - HTTP 5xx 全局兜底提示「服务器异常」，非 5xx 响应清除去重；
 * - 分类：设备无网 → 无网络连接；有网但请求失败 → 无法连接服务器。
 * <p>
 * 见 docs/11-android-network-error.md。
 */
public final class NetworkFeedback {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * 以下静态状态仅允许在主线程读写：
     * - Retrofit enqueue 回调在主线程；
     * - ConnectivityManager NetworkCallback 经主 Looper 投递（MainActivity.onCreate 注册）。
     * 不要在后台线程调用（除 classify/hasNetwork 只读方法外）。
     */
    private static boolean offline = false;          // 断连时段标记：网络级失败/设备断网置位，任一响应清除
    private static boolean serverErrorShown = false; // 5xx 去重标记：5xx 置位，非 5xx 响应清除

    private NetworkFeedback() {}

    /**
     * 网络级失败（无响应）：同一断连时段内只提示一次。
     * 设备无网 → 无网络连接；有网但连不上 → 无法连接服务器。
     */
    public static void reportFailure(Context context) {
        if (offline) return;
        offline = true;
        toast(context, hasNetwork(context) ? R.string.cannot_reach_server : R.string.network_no_connection);
    }

    /**
     * 设备断网（ConnectivityManager onLost）：直接进入断连时段并提示「无网络连接」。
     * 不依赖 {@link #hasNetwork} 瞬时状态——断网瞬间 getActiveNetwork 可能仍返回旧网络。
     */
    public static void reportDeviceOffline(Context context) {
        if (offline) return;
        offline = true;
        toast(context, R.string.network_no_connection);
    }

    /**
     * 收到任何 HTTP 响应：视为恢复，结束断连时段并提示「网络已恢复」（每时段一次）。
     */
    public static void reportContact(Context context) {
        if (offline) {
            offline = false;
            toast(context, R.string.network_recovered);
        }
    }

    /** HTTP 5xx：全局兜底提示「服务器异常」（去重） */
    public static void reportServerError(Context context) {
        if (serverErrorShown) return;
        serverErrorShown = true;
        toast(context, R.string.server_error);
    }

    /** 收到非 5xx 响应：清除 5xx 去重标记 */
    public static void reportServerOk(Context context) {
        serverErrorShown = false;
    }

    /** 分类：网络失败时的友好文案资源 id（供登录/服务器配置页内联提示） */
    public static int classify(Context context) {
        return hasNetwork(context) ? R.string.cannot_reach_server : R.string.network_no_connection;
    }

    /** 设备当前是否有可用网络（无法判断时按有网处理，避免误报） */
    public static boolean hasNetwork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private static void toast(Context context, int resId) {
        Context app = context != null ? context.getApplicationContext() : null;
        if (app == null) return;
        MAIN.post(() -> Toast.makeText(app, app.getString(resId), Toast.LENGTH_SHORT).show());
    }
}
