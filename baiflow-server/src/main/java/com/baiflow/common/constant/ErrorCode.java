package com.baiflow.common.constant;

/**
 * 统一业务错误码常量。
 * <p>
 * 所有 {@link com.baiflow.common.exception.BusinessException}
 * 和 {@link com.baiflow.common.entity.ApiResponse} 的错误码均从此处引用。
 */
public final class ErrorCode {

    private ErrorCode() {}

    /** 成功 */
    public static final String OK = "OK";

    /** 未登录或 token 无效 */
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    /** 无权限 */
    public static final String FORBIDDEN = "FORBIDDEN";

    /** 请求参数校验失败 */
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    /** 资源不存在 */
    public static final String NOT_FOUND = "NOT_FOUND";

    /** 文件操作失败 */
    public static final String FILE_OPERATION_FAILED = "FILE_OPERATION_FAILED";

    /** 存储根目录不可用 */
    public static final String STORAGE_ROOT_OFFLINE = "STORAGE_ROOT_OFFLINE";

    /** aria2 或下载引擎错误 */
    public static final String DOWNLOAD_ENGINE_ERROR = "DOWNLOAD_ENGINE_ERROR";

    /** 分享链接无效 */
    public static final String SHARE_LINK_INVALID = "SHARE_LINK_INVALID";

    /** 分享链接已过期 */
    public static final String SHARE_LINK_EXPIRED = "SHARE_LINK_EXPIRED";

    /** 分享访问或下载次数已达上限 */
    public static final String SHARE_LIMIT_EXCEEDED = "SHARE_LIMIT_EXCEEDED";

    /** 需要提取码 */
    public static final String EXTRACTION_CODE_REQUIRED = "EXTRACTION_CODE_REQUIRED";

    /** 提取码错误 */
    public static final String EXTRACTION_CODE_INVALID = "EXTRACTION_CODE_INVALID";

    /** 需要隐私文件夹密码 */
    public static final String PRIVATE_PASSWORD_REQUIRED = "PRIVATE_PASSWORD_REQUIRED";

    /** 隐私文件夹密码错误 */
    public static final String PRIVATE_PASSWORD_INVALID = "PRIVATE_PASSWORD_INVALID";

    /** 服务端内部错误 */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /** 用户名已存在 */
    public static final String USERNAME_EXISTS = "USERNAME_EXISTS";

    /** 账号已被禁用 */
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

    /** 账号已被锁定 */
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";

    /** 用户名或密码错误 */
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
}
