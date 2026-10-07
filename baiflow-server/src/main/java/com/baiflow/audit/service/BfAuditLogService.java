package com.baiflow.audit.service;

import com.baiflow.audit.constant.AuditTargetType;
import com.baiflow.audit.dto.response.LoginLogVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 审计服务接口 — 异步记录操作审计日志，并提供管理端查询能力。
 */
public interface BfAuditLogService {

    /**
     * 记录一条审计日志。
     * <p>
     * 目标以 {@code targetType} + {@code targetId} 两个参数传入，取值集中在 {@link AuditTargetType}：
     * 四类目标中只有 {@code SYSTEM} 无 ID，故不引入跨参数约束，保持七个参数的直传形态。
     * @param actorUserId 操作者 ID（可为空）
     * @param action      操作类型，取值见 {@link com.baiflow.audit.constant.AuditAction}
     * @param targetType  目标类型，取值见 {@link AuditTargetType}
     * @param targetId    目标 ID（{@code SYSTEM} 目标传 null；登录失败且用户名不存在时为提交上来的用户名）
     * @param ipAddress   IP 地址
     * @param userAgent   User-Agent
     * @param detail      操作详情
     */
    void log(String actorUserId, String action, String targetType, String targetId,
             String ipAddress, String userAgent, String detail);

    /**
     * 分页查询登录日志。
     * @param page      页码（从 1 开始）
     * @param size      每页条数
     * @param username  用户名模糊搜索（可选）
     * @param status    LOGIN_SUCCESS / LOGIN_FAILED（可选）
     * @param startDate 开始日期（可选，格式 yyyy-MM-dd）
     * @param endDate   结束日期（可选，格式 yyyy-MM-dd）
     */
    Page<LoginLogVO> queryLoginLogs(int page, int size, String username, String status,
                                    String startDate, String endDate);
}
