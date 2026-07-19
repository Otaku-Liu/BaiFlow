package com.baiflow.common.entity;

public record ApiResponse<T>(String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Code.OK, "success", data, null);
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(Code.OK, "success", data, traceId);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    public static <T> ApiResponse<T> error(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }

    public static <T> ApiResponse<T> unauthorized(String message) {
        return error(Code.UNAUTHORIZED, message);
    }

    public static <T> ApiResponse<T> forbidden(String message) {
        return error(Code.FORBIDDEN, message);
    }

    public static <T> ApiResponse<T> validationError(String message) {
        return error(Code.VALIDATION_ERROR, message);
    }

    public static <T> ApiResponse<T> notFound(String message) {
        return error(Code.NOT_FOUND, message);
    }

    public static <T> ApiResponse<T> internalError(String message, String traceId) {
        return error(Code.INTERNAL_ERROR, message, traceId);
    }

    public static final class Code {
        private Code() {}
        public static final String OK = "OK";
        public static final String UNAUTHORIZED = "UNAUTHORIZED";
        public static final String FORBIDDEN = "FORBIDDEN";
        public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
        public static final String NOT_FOUND = "NOT_FOUND";
        public static final String FILE_OPERATION_FAILED = "FILE_OPERATION_FAILED";
        public static final String STORAGE_ROOT_OFFLINE = "STORAGE_ROOT_OFFLINE";
        public static final String DOWNLOAD_ENGINE_ERROR = "DOWNLOAD_ENGINE_ERROR";
        public static final String SHARE_LINK_INVALID = "SHARE_LINK_INVALID";
        public static final String SHARE_LINK_EXPIRED = "SHARE_LINK_EXPIRED";
        public static final String SHARE_LIMIT_EXCEEDED = "SHARE_LIMIT_EXCEEDED";
        public static final String EXTRACTION_CODE_REQUIRED = "EXTRACTION_CODE_REQUIRED";
        public static final String EXTRACTION_CODE_INVALID = "EXTRACTION_CODE_INVALID";
        public static final String PRIVATE_PASSWORD_REQUIRED = "PRIVATE_PASSWORD_REQUIRED";
        public static final String PRIVATE_PASSWORD_INVALID = "PRIVATE_PASSWORD_INVALID";
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
        public static final String USERNAME_EXISTS = "USERNAME_EXISTS";
        public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
        public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
        public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    }
}
