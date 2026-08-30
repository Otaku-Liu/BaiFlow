package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/** 下载记录（对应后端 DownloadRecordInfo） */
public class DownloadRecord {
    @SerializedName("id") private String id;
    @SerializedName("fileId") private String fileId;
    @SerializedName("fileName") private String fileName;
    @SerializedName("downloaderUserId") private String downloaderUserId;
    @SerializedName("downloaderUsername") private String downloaderUsername;
    @SerializedName("source") private String source;
    @SerializedName("shareId") private String shareId;
    @SerializedName("ipAddress") private String ipAddress;
    @SerializedName("createdAt") private String createdAt;

    public String getFileName() { return fileName; }
    public String getDownloaderUsername() { return downloaderUsername; }
    public String getSource() { return source; }
    public String getIpAddress() { return ipAddress; }
    public String getCreatedAt() { return createdAt; }
}
