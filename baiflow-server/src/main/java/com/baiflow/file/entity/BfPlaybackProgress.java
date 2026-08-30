package com.baiflow.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 播放/阅读进度实体 — 跨设备断点续看 */
@Data
@TableName("bf_playback_progress")
public class BfPlaybackProgress {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String fileItemId;
    private String positionType;
    private Double positionValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
