package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * 笔记列表项（不含正文，正文在详情接口返回）。
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

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
