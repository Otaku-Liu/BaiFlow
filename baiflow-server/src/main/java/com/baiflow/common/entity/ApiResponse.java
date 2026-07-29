package com.baiflow.common.entity;

import com.baiflow.common.constant.ErrorCode;

public record ApiResponse<T>(String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.OK, "success", data, null);
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(ErrorCode.OK, "success", data, traceId);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    public static <T> ApiResponse<T> error(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }

    public static <T> ApiResponse<T> unauthorized(String message) {
        return error(ErrorCode.UNAUTHORIZED, message);
    }

    public static <T> ApiResponse<T> forbidden(String message) {
        return error(ErrorCode.FORBIDDEN, message);
    }

    public static <T> ApiResponse<T> validationError(String message) {
        return error(ErrorCode.VALIDATION_ERROR, message);
    }

    public static <T> ApiResponse<T> notFound(String message) {
        return error(ErrorCode.NOT_FOUND, message);
    }

    public static <T> ApiResponse<T> internalError(String message, String traceId) {
        return error(ErrorCode.INTERNAL_ERROR, message, traceId);
    }
}
