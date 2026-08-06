package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * 笔记阅读进度 — Android 编辑器非阅读器，本模型仅用于定义接口契约，
 * 客户端不主动上报（进度由 Web 阅读时维护）。
 */
public class NoteProgress {
    @SerializedName("noteId")
    private String noteId;
    @SerializedName("positionType")
    private String positionType;
    @SerializedName("positionValue")
    private double positionValue;
    @SerializedName("updatedAt")
    private String updatedAt;

    public String getNoteId() { return noteId; }
    public String getPositionType() { return positionType; }
    public double getPositionValue() { return positionValue; }
    public String getUpdatedAt() { return updatedAt; }
}
