package com.baiflow.audit.constant;

/**
 * 审计日志操作类型（{@code bf_audit_log.action}）取值 —— 认证、初始化、定时任务等模块共用。
 * <p>
 * <b>字符串值是对外契约，只增不改。</b>这些值同时被以下位置按字面量匹配：
 * <ul>
 *   <li>DB 列值（{@code bf_audit_log.action}）</li>
 *   <li>{@code BfAuditLogMapper.xml} 的 {@code action IN (...)} 过滤</li>
 *   <li>{@code GET /api/admin/audit-logs/login} 的 {@code status} 查询参数</li>
 *   <li>Web 端 {@code LoginLogsView.vue} 的筛选下拉、文案与标签颜色映射</li>
 * </ul>
 * 改名或改值会同时打断入库、查询过滤与前端展示，只能新增取值。
 * <p>
 * 仅登录与会话相关的 7 个取值（标注「登录日志可见」）会出现在管理员登录日志页；
 * 其余只入库、不在该页展示。
 */
public final class AuditAction {

    private AuditAction() {}

    /** 登录成功（登录日志可见） */
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";

    /** 登录失败：账号已锁定 / 用户名不存在 / 已禁用 / 已锁定 / 密码错误（登录日志可见） */
    public static final String LOGIN_FAILED = "LOGIN_FAILED";

    /** 登出（登录日志可见） */
    public static final String LOGOUT = "LOGOUT";

    /** 强制下线设备，撤销该设备全部会话（登录日志可见） */
    public static final String FORCE_LOGOUT = "FORCE_LOGOUT";

    /** 修改密码并吊销全部登录会话（登录日志可见） */
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    /** 登录失败次数达阈值，账号自动锁定（登录日志可见） */
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";

    /** 锁定到期，账号自动恢复为正常（登录日志可见） */
    public static final String ACCOUNT_UNLOCKED = "ACCOUNT_UNLOCKED";

    /** 删除离线登录设备（仅入库，登录日志页不展示） */
    public static final String DELETE_DEVICE = "DELETE_DEVICE";

    /** 首次部署初始化完成，创建管理员（仅入库，登录日志页不展示） */
    public static final String SYSTEM_SETUP = "SYSTEM_SETUP";

    /** 初始化令牌校验失败（仅入库，登录日志页不展示） */
    public static final String SYSTEM_SETUP_FAILED = "SYSTEM_SETUP_FAILED";
}
