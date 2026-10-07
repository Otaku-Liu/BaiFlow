package com.baiflow.common.entity;

import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.filter.TraceIdFilter;
import org.slf4j.MDC;

public record ApiResponse<T>(int code, String message, T data, String traceId) {

    /** 当前请求的链路 id（由 {@code TraceIdFilter} 写进 MDC）；非 Web 线程为 null */
    private static String currentTraceId() {
        return MDC.get(TraceIdFilter.MDC_KEY);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.OK, "success", data, currentTraceId());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, currentTraceId());
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

    public static <T> ApiResponse<T> internalError(String message) {
        return error(ErrorCode.INTERNAL_ERROR, message);
    }
}
