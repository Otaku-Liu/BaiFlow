package com.baiflow.common.config;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private MessageSource messageSource;

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Object> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        String message = resolveMessage(ex.getCode(), ex.getMessage());
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
        String message = resolveMessage("access_denied", "Insufficient privileges");
        return ApiResponse.forbidden(message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Object> handleException(Exception ex, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.error("Unhandled exception traceId={}", traceId, ex);
        String message = resolveMessage("unexpected_error", "Unexpected server error");
        return ApiResponse.internalError(message, traceId);
    }

    /** 尝试用 i18n message key 解析，失败回退到 fallback */
    private String resolveMessage(String code, String fallback) {
        try {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(code, null, fallback, locale);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }
}
