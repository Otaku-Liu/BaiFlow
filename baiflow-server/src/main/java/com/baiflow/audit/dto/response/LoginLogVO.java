package com.baiflow.audit.dto.response;

import java.time.LocalDateTime;

/** 登录日志条目 — 用于 ADMIN 审计面板查询。 */
public record LoginLogVO(
        String id,
        String userId,
        String username,
        String displayName,
        String action,
        String ipAddress,
        String userAgent,
        String detail,
        LocalDateTime createdAt) {
}
