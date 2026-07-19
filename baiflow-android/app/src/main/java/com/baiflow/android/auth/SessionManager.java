package com.baiflow.android.auth;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 会话管理器 — 使用 SharedPreferences 存储 token、服务器地址和用户信息。
 * <p>
 * 负责登录态维护：token 存取、登录状态判断、清除会话。
 */
public class SessionManager {
    private static final String PREF_NAME = "baiflow_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_ROLE = "role";

    private static SessionManager instance;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
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
    public void saveUser(String username, String displayName, String role) {
        prefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_DISPLAY_NAME, displayName)
                .putString(KEY_ROLE, role)
                .apply();
    }
    public String getUsername() { return prefs.getString(KEY_USERNAME, null); }
    public String getDisplayName() { return prefs.getString(KEY_DISPLAY_NAME, null); }
    public String getRole() { return prefs.getString(KEY_ROLE, null); }

    // ---- Clear ----
    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
