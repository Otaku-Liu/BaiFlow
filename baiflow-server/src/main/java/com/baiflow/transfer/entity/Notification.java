package com.baiflow.transfer.entity;

import com.baiflow.transfer.enums.NotificationLevel;
import com.baiflow.transfer.enums.ReadStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知实体 — 用户级别的系统通知。
 */
@Data
@TableName("notification")
public class Notification {

    /** 主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 目标用户 ID */
    private String userId;

    /** 通知级别：INFO / WARN / ERROR */
    private NotificationLevel level;

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 阅读状态：UNREAD（未读）/ READ（已读） */
    private ReadStatus readStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 标记已读的时间 */
    private LocalDateTime readAt;
}
