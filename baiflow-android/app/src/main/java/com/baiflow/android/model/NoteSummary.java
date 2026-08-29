package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * 笔记列表项（普通列表不含正文；增量同步模式（updatedAfter）携带 content 供直接合并）。
 */
public class NoteSummary {
    @SerializedName("id")
    private String id;
    @SerializedName("title")
    private String title;
    @SerializedName("status")
    private String status;
    @SerializedName("createdAt")
    private String createdAt;
    @SerializedName("updatedAt")
    private String updatedAt;
    @SerializedName("content")
    private String content;

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public String getContent() { return content; }
}
