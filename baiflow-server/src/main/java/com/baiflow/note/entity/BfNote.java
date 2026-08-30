package com.baiflow.note.entity;

import com.baiflow.note.enums.NoteStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 随手记笔记实体 — 标题 + Markdown 正文。
 * <p>
 * 笔记独立于文件系统存储，正文直接落库；时间戳由数据库默认值管理，
 * 更新时服务端显式刷新 updated_at 以支撑后写覆盖同步。
 */
@Data
@TableName("bf_note")
public class BfNote {
    /** 主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所有者用户 ID */
    private String userId;

    /** 标题 */
    private String title;

    /** Markdown 正文 */
    private String content;

    /** 状态：ACTIVE（正常）/ DELETED（软删除） */
    private NoteStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
