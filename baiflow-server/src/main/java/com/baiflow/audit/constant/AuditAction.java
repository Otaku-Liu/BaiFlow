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
 * 改名或改值会同时打断入库、查询过滤与前端展示，因此只增不改值、不改名。
 * <p>
 * 删除一个已不再写入的取值也可以，但前提是那批历史行可以不再可查：取值移出
 * {@code action IN (...)} 过滤名单后，库里带该值的历史行在登录日志页再也查不出来。
 * 所以删之前要么清掉这些行、要么确认它们不需要再查（删 {@code ACCOUNT_UNLOCKED} 时
 * 以整库重来为前提，未做数据清理）。
 * <p>
 * 仅登录与会话相关的 6 个取值（标注「登录日志可见」）会出现在管理员登录日志页；
 * 其余只入库、不在该页展示。
 */
public final class AuditAction {

    private AuditAction() {}

    /** 登录成功（登录日志可见） */
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";

    /** 登录失败：账号已被临时锁定 / 用户名不存在 / 账号已禁用 / 密码错误（登录日志可见） */
    public static final String LOGIN_FAILED = "LOGIN_FAILED";

    /** 登出（登录日志可见） */
    public static final String LOGOUT = "LOGOUT";

    /** 强制下线设备，撤销该设备全部会话（登录日志可见） */
    public static final String FORCE_LOGOUT = "FORCE_LOGOUT";

    /** 修改密码并吊销全部登录会话（登录日志可见） */
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    /** 登录失败次数达阈值，该用户名被临时锁定（target 为用户名，可能并不存在该账号；登录日志可见） */
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";

    /** 删除离线登录设备（仅入库，登录日志页不展示） */
    public static final String DELETE_DEVICE = "DELETE_DEVICE";

    /** 首次部署初始化完成，创建管理员（仅入库，登录日志页不展示） */
    public static final String SYSTEM_SETUP = "SYSTEM_SETUP";

    /** 初始化令牌校验失败（仅入库，登录日志页不展示） */
    public static final String SYSTEM_SETUP_FAILED = "SYSTEM_SETUP_FAILED";
}
