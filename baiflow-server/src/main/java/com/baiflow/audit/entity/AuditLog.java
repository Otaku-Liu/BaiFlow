package com.baiflow.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作审计日志实体 */
@Data
@TableName("bf_audit_log")
public class AuditLog {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 操作者用户 ID（匿名操作为空） */
    private String actorUserId;

    /** 操作类型：LOGIN_SUCCESS / LOGIN_FAILED / FILE_DELETE / SHARE_CREATE / SHARE_ACCESS / SHARE_REVOKE 等 */
    private String action;

    /** 操作目标类型：USER / FILE / SHARE_LINK 等 */
    private String targetType;

    /** 操作目标 ID */
    private String targetId;

    /** 操作者 IP 地址 */
    private String ipAddress;

    /** 操作者 User-Agent */
    private String userAgent;

    /** 操作详情（补充描述） */
    private String detail;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
