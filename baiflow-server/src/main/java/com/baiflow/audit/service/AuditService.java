package com.baiflow.audit.service;

/**
 * 审计服务接口 — 异步记录操作审计日志。
 */
public interface AuditService {
    /**
     * 记录一条审计日志。
     * @param actorUserId 操作者 ID（可为空）
     * @param action      操作类型
     * @param targetType  目标类型
     * @param targetId    目标 ID
     * @param ipAddress   IP 地址
     * @param userAgent   User-Agent
     * @param detail      操作详情
     */
    void log(String actorUserId, String action, String targetType, String targetId,
             String ipAddress, String userAgent, String detail);
}
