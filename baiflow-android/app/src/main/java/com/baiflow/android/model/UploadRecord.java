package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/** 上传记录（对应后端 UploadRecordInfo） */
public class UploadRecord {
    @SerializedName("id") private String id;
    @SerializedName("fileId") private String fileId;
    @SerializedName("fileName") private String fileName;
    @SerializedName("uploaderUserId") private String uploaderUserId;
    @SerializedName("uploaderUsername") private String uploaderUsername;
    @SerializedName("source") private String source;
    @SerializedName("ipAddress") private String ipAddress;
    @SerializedName("createdAt") private String createdAt;

    public String getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public String getUploaderUsername() { return uploaderUsername; }
    public String getSource() { return source; }
    public String getIpAddress() { return ipAddress; }
    public String getCreatedAt() { return createdAt; }
}
