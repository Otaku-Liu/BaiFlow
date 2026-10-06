package com.baiflow.audit.constant;

/**
 * 审计日志操作目标类型（{@code bf_audit_log.target_type}）取值。
 * <p>
 * <b>字符串值只增不改</b>：作为 DB 列值落库，并建有复合索引 {@code idx_al_target(target_type, target_id)}。
 * 与 {@link AuditAction} 不同，当前没有任何查询或前端按此列过滤 —— 改值不会立即打断展示，但会让历史行与新值并存。
 */
public final class AuditTargetType {

    private AuditTargetType() {}

    /** 用户（targetId = 用户 ID；登录失败且用户名不存在时，targetId 为提交上来的用户名） */
    public static final String USER = "USER";

    /** 登录会话（targetId = 会话 ID） */
    public static final String SESSION = "SESSION";

    /** 登录设备（targetId = 设备名） */
    public static final String DEVICE = "DEVICE";

    /** 系统级事件（targetId 为空） */
    public static final String SYSTEM = "SYSTEM";
}
