package com.baiflow.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 笔记阅读进度实体 — 跨设备续读（position_type 为 SCROLL_PERCENT） */
@Data
@TableName("bf_note_progress")
public class NoteProgress {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String noteId;
    private String positionType;
    private Double positionValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
