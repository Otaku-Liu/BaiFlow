package com.baiflow.android.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * 本机下载完成时的保存位置记录（fileId → 位置）。
 * <p>下载记录（服务端）不含本机保存位置；DownloadService 完成时写入，
 * 下载记录详情据此展示「保存位置」并用系统查看器打开（仅本机下载过的文件可用）。
 */
public final class DownloadLocationStore {

    private static final String PREFS = "download_locations";

    private DownloadLocationStore() {
    }

    /** 记录一次下载的保存位置；fileId 为空忽略。uri 与 filePath 二选一（API 29+ 记 content URI，26-28 记绝对路径） */
    public static void save(Context context, String fileId, String displayName, String uri, String filePath) {
        if (fileId == null || fileId.isEmpty()) {
            return;
        }
        SharedPreferences sp = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(fileId, toJson(displayName, uri, filePath)).apply();
    }

    /** 读取本机保存位置；无记录返回 null */
    public static Location load(Context context, String fileId) {
        if (fileId == null) {
            return null;
        }
        SharedPreferences sp = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = sp.getString(fileId, null);
        if (json == null) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(json);
            return new Location(o.optString("displayName"), o.optString("uri", null), o.optString("filePath", null));
        } catch (Exception e) {
            return null;
        }
    }

    private static String toJson(String displayName, String uri, String filePath) {
        try {
            JSONObject o = new JSONObject();
            o.put("displayName", displayName != null ? displayName : "");
            if (uri != null) {
                o.put("uri", uri);
            }
            if (filePath != null) {
                o.put("filePath", filePath);
            }
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 本机一次下载的保存位置 */
    public static class Location {
        public final String displayName;
        /** API 29+ MediaStore content URI；可能为 null（26-28 走 filePath） */
        public final String uri;
        /** API 26-28 绝对路径；可能为 null（29+ 走 uri） */
        public final String filePath;

        Location(String displayName, String uri, String filePath) {
            this.displayName = displayName;
            this.uri = uri;
            this.filePath = filePath;
        }
    }
}
