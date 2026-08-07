package com.baiflow.common.config;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.common.util.I18nUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 兜底错误文案（BusinessException 消息为空时使用） */
    private static final String GENERIC_ERROR_MSG = "服务器内部错误，请稍后再试";

    @Autowired
    private I18nUtil i18nUtil;

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Object> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        String message = i18nUtil.translate(ex.getMessage());
        if (message == null || message.isBlank()) {
            message = i18nUtil.translate(GENERIC_ERROR_MSG);
        }
        return ApiResponse.error(ex.getCode(), message, resolveTraceId(request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ApiResponse.validationError(message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Object> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String message = i18nUtil.translate("权限不足");
        return ApiResponse.forbidden(message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Object> handleException(Exception ex, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.error("Unhandled exception traceId={}", traceId, ex);
        String message = i18nUtil.translate(GENERIC_ERROR_MSG);
        return ApiResponse.internalError(message, traceId);
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }
}
