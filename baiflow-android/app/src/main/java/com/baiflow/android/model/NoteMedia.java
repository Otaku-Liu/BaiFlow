package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * 笔记媒体上传响应 — 含访问 URL（相对路径，Android 拼接服务器地址后经鉴权接口读取）。
 */
public class NoteMedia {
    @SerializedName("id")
    private String id;
    @SerializedName("mediaType")
    private String mediaType;
    @SerializedName("url")
    private String url;
    @SerializedName("mimeType")
    private String mimeType;
    @SerializedName("sizeBytes")
    private long sizeBytes;
    @SerializedName("createdAt")
    private String createdAt;

    public String getId() { return id; }
    public String getMediaType() { return mediaType; }
    public String getUrl() { return url; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getCreatedAt() { return createdAt; }
}
