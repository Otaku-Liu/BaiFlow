package com.baiflow.common.constant;

/**
 * 统一业务错误码常量（数字码）。
 * <p>
 * 编码规则：5 位结构化数字码，按错误域分组——
 * <pre>
 * 0       成功
 * 400xx   参数/请求校验
 * 401xx   认证（未登录、凭证错误、提取码、隐私密码）
 * 403xx   权限不足
 * 404xx   资源不存在
 * 409xx   冲突（并发、重复）
 * 410xx   已过期
 * 423xx   已锁定/禁用
 * 429xx   超限（频率/次数）
 * 500xx   服务端/文件/存储/下载引擎错误
 * </pre>
 * 所有 {@link com.baiflow.common.exception.BusinessException}
 * 和 {@link com.baiflow.common.entity.ApiResponse} 的错误码均从此处引用。
 * 数字码同时作为 i18n 词条定位依据，客户端按数字码区分错误分支。
 */
public final class ErrorCode {

    private ErrorCode() {}

    /** 成功 */
    public static final int OK = 0;

    /** 请求参数校验失败 */
    public static final int VALIDATION_ERROR = 40001;

    /** 未登录或 token 无效 */
    public static final int UNAUTHORIZED = 40101;

    /** 用户名或密码错误 */
    public static final int INVALID_CREDENTIALS = 40102;

    /** 需要提取码 */
    public static final int EXTRACTION_CODE_REQUIRED = 40103;

    /** 提取码错误 */
    public static final int EXTRACTION_CODE_INVALID = 40104;

    /** 需要隐私文件夹密码 */
    public static final int PRIVATE_PASSWORD_REQUIRED = 40105;

    /** 隐私文件夹密码错误 */
    public static final int PRIVATE_PASSWORD_INVALID = 40106;

    /** 无权限 */
    public static final int FORBIDDEN = 40301;

    /** 资源不存在 */
    public static final int NOT_FOUND = 40401;

    /** 分享链接无效 */
    public static final int SHARE_LINK_INVALID = 40402;

    /** 笔记已被其他设备修改（乐观并发冲突） */
    public static final int NOTE_CONFLICT = 40901;

    /** 用户名已存在 */
    public static final int USERNAME_EXISTS = 40902;

    /** 分享链接已过期 */
    public static final int SHARE_LINK_EXPIRED = 41001;

    /** 账号已被锁定 */
    public static final int ACCOUNT_LOCKED = 42301;

    /** 账号已被禁用 */
    public static final int ACCOUNT_DISABLED = 42302;

    /** 分享访问或下载次数已达上限 */
    public static final int SHARE_LIMIT_EXCEEDED = 42901;

    /** 服务端内部错误 */
    public static final int INTERNAL_ERROR = 50000;

    /** 文件操作失败 */
    public static final int FILE_OPERATION_FAILED = 50001;

    /** 存储根目录不可用 */
    public static final int STORAGE_ROOT_OFFLINE = 50002;
}
