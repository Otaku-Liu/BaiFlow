package com.baiflow.android.data;

import android.content.Context;

import java.io.File;

/**
 * 笔记媒体本地文件工具。
 * <p>
 * - 离线新建媒体：存 {@code filesDir/note_media/<fileName>}，正文用 {@code local://<fileName>} 引用；
 * - 服务端媒体缓存：存 {@code filesDir/note_media_cache/<mediaId>}，离线可读（正文仍是 /api/notes/media/{id}）。
 */
public final class MediaFiles {

    private MediaFiles() {}

    /** 离线新建媒体的目录 */
    public static File localMediaDir(Context context) {
        File dir = new File(context.getFilesDir(), "note_media");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 服务端媒体缓存目录 */
    public static File cacheDir(Context context) {
        File dir = new File(context.getFilesDir(), "note_media_cache");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 构造 local:// 引用（fileName 为 note_media 下的文件名） */
    public static String localUrl(String fileName) {
        return "local://" + fileName;
    }

    /** 本地引用对应的文件（url 形如 local://xxx[?mediaType=audio]） */
    public static File localFile(Context context, String url) {
        if (url == null || !url.startsWith("local://")) return null;
        String name = url.substring("local://".length());
        int q = name.indexOf('?');
        if (q >= 0) name = name.substring(0, q);
        if (name.isEmpty()) return null;
        return new File(localMediaDir(context), name);
    }

    /** 服务端媒体缓存文件（url 形如 /api/notes/media/{id}） */
    public static File cachedMediaFile(Context context, String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) return null;
        return new File(cacheDir(context), mediaId);
    }

    /**
     * 把正文里的媒体 URL 解析成本地可读文件（若存在）：
     * local:// → 离线新建文件；/api/notes/media/{id} → 同步时缓存的文件；否则 null。
     */
    public static File resolveLocal(Context context, String url) {
        if (url == null) return null;
        if (url.startsWith("local://")) {
            return localFile(context, url);
        }
        int idx = url.indexOf("/api/notes/media/");
        if (idx >= 0) {
            String rest = url.substring(idx + "/api/notes/media/".length());
            int q = rest.indexOf('?');
            String mediaId = q >= 0 ? rest.substring(0, q) : rest;
            File cached = cachedMediaFile(context, mediaId);
            return cached != null && cached.exists() ? cached : null;
        }
        return null;
    }
}
