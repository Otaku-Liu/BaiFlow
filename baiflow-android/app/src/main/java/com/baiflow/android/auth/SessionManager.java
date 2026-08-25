package com.baiflow.android.auth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.baiflow.android.ui.activity.LoginActivity;

/**
 * 会话管理器 — 使用 SharedPreferences 存储 token、服务器地址和用户信息。
 * <p>
 * 负责登录态维护：token 存取、登录状态判断、清除会话；会话被吊销（401）时跳登录页。
 */
public class SessionManager {
    private static final String PREF_NAME = "baiflow_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_ROLE = "role";

    private static SessionManager instance;
    private final Context appContext;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) { instance = new SessionManager(context); }
        return instance;
    }

    // ---- Token ----
    public void saveToken(String token) { prefs.edit().putString(KEY_TOKEN, token).apply(); }
    public String getToken() { return prefs.getString(KEY_TOKEN, null); }
    public boolean isLoggedIn() { return getToken() != null && !getToken().isEmpty(); }

    // ---- Server URL ----
    public void saveServerUrl(String url) {
        // 规范化：去掉尾部斜杠
        String normalized = url != null ? url.replaceAll("/+$", "") : "";
        prefs.edit().putString(KEY_SERVER_URL, normalized).apply();
    }
    public String getServerUrl() { return prefs.getString(KEY_SERVER_URL, null); }
    public String getApiBaseUrl() {
        String server = getServerUrl();
        return server != null ? server + "/api/" : null;
    }

    // ---- User ----
    public void saveUser(String id, String username, String displayName, String avatarUrl, String role) {
        prefs.edit()
                .putString(KEY_USER_ID, id)
                .putString(KEY_USERNAME, username)
                .putString(KEY_DISPLAY_NAME, displayName)
                .putString(KEY_AVATAR_URL, avatarUrl)
                .putString(KEY_ROLE, role)
                .apply();
    }
    public String getUserId() { return prefs.getString(KEY_USER_ID, null); }

    /** 单独更新展示名（修改资料后本地同步） */
    public void saveDisplayName(String displayName) {
        prefs.edit().putString(KEY_DISPLAY_NAME, displayName).apply();
    }
    public String getUsername() { return prefs.getString(KEY_USERNAME, null); }
    public String getDisplayName() { return prefs.getString(KEY_DISPLAY_NAME, null); }

    /** 单独更新头像 URL（更换头像后本地同步） */
    public void saveAvatarUrl(String avatarUrl) {
        prefs.edit().putString(KEY_AVATAR_URL, avatarUrl).apply();
    }
    public String getAvatarUrl() { return prefs.getString(KEY_AVATAR_URL, null); }
    public String getRole() { return prefs.getString(KEY_ROLE, null); }

    // ---- 离线模式（三态）----

    /** 本地模式分区键（未配服务器） */
    public static final String PARTITION_LOCAL = "LOCAL";

    private static final String KEY_OFFLINE = "offline";
    private static final String KEY_LAST_SYNC_AT = "last_sync_at";
    private static final String KEY_GUIDE_SHOWN = "guide_shown";

    /** 首次启动引导页是否已展示（设置服务器 / 先本地用 二选一） */
    public boolean isGuideShown() {
        return prefs.getBoolean(KEY_GUIDE_SHOWN, false);
    }

    public void saveGuideShown() {
        prefs.edit().putBoolean(KEY_GUIDE_SHOWN, true).apply();
    }

    /** 进离线：清 token + 用户，但保留服务器地址与离线标记（本地笔记可继续用） */
    public void enterOfflineMode() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_DISPLAY_NAME)
                .remove(KEY_AVATAR_URL)
                .remove(KEY_ROLE)
                .putBoolean(KEY_OFFLINE, true)
                .apply();
    }

    public void saveOffline(boolean offline) {
        prefs.edit().putBoolean(KEY_OFFLINE, offline).apply();
    }

    public boolean isOffline() {
        return prefs.getBoolean(KEY_OFFLINE, false);
    }

    public String getLastSyncAt() {
        return prefs.getString(KEY_LAST_SYNC_AT, null);
    }

    public void saveLastSyncAt(String iso) {
        prefs.edit().putString(KEY_LAST_SYNC_AT, iso != null ? iso : "").apply();
    }

    // ---- 三态判断（见 docs/12-android-offline-mode.md §4）----

    /** 本地模式：未配服务器，免登录纯本地 */
    public boolean isLocalMode() {
        return getServerUrl() == null;
    }

    /** 在线模式：服务器 + token + 非离线 */
    public boolean isOnlineMode() {
        return getServerUrl() != null && isLoggedIn() && !isOffline();
    }

    /**
     * 离线模式：已配服务器且主动离线标记。
     * 注意：已配服务器但未登录且未离线 ≠ 离线模式——那是登录门槛（进登录页）。
     * 成功登录会复位离线标记（见 LoginActivity）。
     */
    public boolean isOfflineMode() {
        return getServerUrl() != null && isOffline();
    }

    /** 本地笔记数据分区键：本地模式 = LOCAL；否则 = 服务器地址（缓存绑定服务器） */
    public String getDataPartition() {
        return isLocalMode() ? PARTITION_LOCAL : getServerUrl();
    }

    // ---- Clear ----

    /**
     * 登出/401：清 token + 用户，保留服务器地址并复位离线标记（停在登录页）。
     * 同时清增量同步游标 lastSyncAt —— 登出等流程会清 Room 分区缓存，若保留旧游标，
     * 重登后增量拉取 updatedAfter=旧时间 会漏掉所有更早的笔记（Room 永远为空）。
     */
    public void clearSession() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_DISPLAY_NAME)
                .remove(KEY_AVATAR_URL)
                .remove(KEY_ROLE)
                .remove(KEY_LAST_SYNC_AT)
                .putBoolean(KEY_OFFLINE, false)
                .apply();
    }

    /** 彻底清除（换服务器为本地模式 / 清除应用数据语义）：清全部 */
    public void clearAll() {
        prefs.edit().clear().apply();
    }

    /**
     * 会话被吊销（401）：清会话并强制回到登录页。
     * 长会话被 Web 端强制下线/过期时，网络层收到 401 调用本方法，把用户踢回登录界面
     * （而非停留在原页面只报「无法浏览数据」）。
     */
    public void kickToLogin() {
        clearSession();
        final Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            Intent intent = new Intent(ctx, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ctx.startActivity(intent);
        });
    }
}
