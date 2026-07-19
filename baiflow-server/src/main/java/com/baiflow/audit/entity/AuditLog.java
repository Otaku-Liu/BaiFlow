package com.baiflow.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作审计日志实体 */
@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String actorUserId;
    private String action;
    private String targetType;
    private String targetId;
    private String ipAddress;
    private String userAgent;
    private String detail;
    private LocalDateTime createdAt;
}
