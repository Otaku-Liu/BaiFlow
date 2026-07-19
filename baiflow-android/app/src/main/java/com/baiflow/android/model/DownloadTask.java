package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * 下载任务信息。
 */
public class DownloadTask {
    @SerializedName("id")
    private String id;
    @SerializedName("sourceUrl")
    private String sourceUrl;
    @SerializedName("fileName")
    private String fileName;
    @SerializedName("status")
    private String status; // WAITING/RUNNING/PAUSED/FAILED/COMPLETED/DELETED
    @SerializedName("progress")
    private int progress;
    @SerializedName("totalBytes")
    private long totalBytes;
    @SerializedName("completedBytes")
    private long completedBytes;
    @SerializedName("speedBytesPerSecond")
    private long speedBytesPerSecond;
    @SerializedName("errorMessage")
    private String errorMessage;
    @SerializedName("createdAt")
    private String createdAt;

    public String getId() { return id; }
    public String getSourceUrl() { return sourceUrl; }
    public String getFileName() { return fileName; }
    public String getStatus() { return status; }
    public int getProgress() { return progress; }
    public long getTotalBytes() { return totalBytes; }
    public long getCompletedBytes() { return completedBytes; }
    public long getSpeedBytesPerSecond() { return speedBytesPerSecond; }
    public String getErrorMessage() { return errorMessage; }
    public String getCreatedAt() { return createdAt; }
}
