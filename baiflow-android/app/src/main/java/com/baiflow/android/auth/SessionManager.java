package com.baiflow.android.auth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.baiflow.android.BuildConfig;
import com.baiflow.android.ui.activity.LoginActivity;

/**
 * 会话管理器 — 使用 SharedPreferences 存储 token 和用户信息；服务器地址由构建类型固定。
 * <p>
 * 负责登录态维护：token 存取、登录状态判断、清除会话；会话被吊销（401）时跳登录页。
 */
public class SessionManager {
    private static final String PREF_NAME = "baiflow_session";
    /** 文件目录导航栈持久化文件名（FilesFragment 使用；登出时一并清除避免残留上一用户目录） */
    public static final String PREFS_FILES_NAV = "files_nav";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_ROLE = "role";
    private static final String KEY_LANGUAGE = "language";

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

    // ---- Server URL（固定由构建类型决定，不再手动配置）----
    // 真实地址在本地 local.properties（git 忽略），见 ../local.properties.example
    public String getServerUrl() {
        // 去尾部斜杠（local.properties 里配 http://host:port/ 也能归一），避免拼出 //api/
        String url = BuildConfig.SERVER_URL;
        return url != null ? url.replaceAll("/+$", "") : "";
    }
    public String getApiBaseUrl() { return getServerUrl() + "/api/"; }

    // ---- Language（应用语言，BaseActivity 应用；替代 AppCompatDelegate.setApplicationLocales） ----
    public void saveLanguage(String lang) { prefs.edit().putString(KEY_LANGUAGE, lang).apply(); }
    public String getLanguage() { return prefs.getString(KEY_LANGUAGE, ""); }

    // ---- 缓存上限（MB）：媒体缓存超限后自动 LRU 清理 ----
    private static final String KEY_CACHE_LIMIT_MB = "cache_limit_mb";
    private static final int DEFAULT_CACHE_LIMIT_MB = 300;

    public int getCacheLimitMb() { return prefs.getInt(KEY_CACHE_LIMIT_MB, DEFAULT_CACHE_LIMIT_MB); }
    public void saveCacheLimitMb(int mb) { prefs.edit().putInt(KEY_CACHE_LIMIT_MB, mb).apply(); }

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

    // ---- 增量同步游标 ----
    private static final String KEY_LAST_SYNC_AT = "last_sync_at";

    public String getLastSyncAt() {
        return prefs.getString(KEY_LAST_SYNC_AT, null);
    }

    public void saveLastSyncAt(String iso) {
        prefs.edit().putString(KEY_LAST_SYNC_AT, iso != null ? iso : "").apply();
    }

    // ---- 模式判断（仅在线模式；离线/本地模式已移除）----

    /** 在线模式：已登录即在线 */
    public boolean isOnlineMode() {
        return isLoggedIn();
    }

    /** 本地笔记数据分区键 = 服务器地址（缓存绑定服务器） */
    public String getDataPartition() {
        return getServerUrl();
    }

    // ---- Clear ----

    /**
     * 登出/401：清 token + 用户，保留服务器地址（停在登录页）。
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
                .apply();
        // 清文件目录导航栈持久化，避免换账号/登出后残留上一用户目录
        appContext.getSharedPreferences(PREFS_FILES_NAV, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** 彻底清除（清除应用数据语义）：清全部 */
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
