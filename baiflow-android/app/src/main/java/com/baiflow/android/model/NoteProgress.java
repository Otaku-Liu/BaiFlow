package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * 笔记阅读进度（SCROLL_PERCENT，0~1）— Android 编辑器续读时上报，与 Web 共用同一份数据。
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
