package com.baiflow.common.config;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.common.filter.TraceIdFilter;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.common.util.I18nUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    /** 兜底错误文案（BusinessException 消息为空时使用） */
    private static final String GENERIC_ERROR_MSG = "服务器内部错误，请稍后再试";

    private final I18nUtil i18nUtil;

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Object> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        String message = i18nUtil.translate(ex.getMessage());
        if (message == null || message.isBlank()) {
            message = i18nUtil.translate(GENERIC_ERROR_MSG);
        }
        // 业务失败必须留痕：否则「这个请求为什么被拒」在服务端日志里完全看不见，
        // 排查时只能去翻客户端拿到的响应（请求日志里入参是原样记录的，见 docs/01「日志与可观测性」）
        log.warn("业务失败 code={} {} {} message={}",
                ex.getCode(), request.getMethod(), request.getRequestURI(), message);
        return ApiResponse.error(ex.getCode(), message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        // 参数校验失败同样留痕：这是最典型的「传参问题」，报的是字段名与约束，不含字段值
        log.warn("参数校验失败 {} {} message={}",
                request.getMethod(), request.getRequestURI(), message);
        return ApiResponse.validationError(message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Object> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String message = i18nUtil.translate("权限不足");
        // 权限拒绝是安全相关事件：谁、什么时候、试了哪个接口，要能查
        log.warn("权限拒绝 {} {} message={}", request.getMethod(), request.getRequestURI(), message);
        return ApiResponse.forbidden(message);
    }

    /**
     * 唯一键冲突：并发下的「同时创建同一个东西」（同名文件/目录、主目录/隐私空间的首次并发创建）。
     * 正常路径都会先查再写并给出友好提示，能走到这里说明正好撞上竞态 —— 提示重试即可，
     * 对用户是「偶发的一次重试」，而不是 500。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Object> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest request) {
        String message = i18nUtil.translate("目标已存在（可能是并发重复提交），请刷新后重试");
        log.warn("唯一键冲突 {} {} message={}", request.getMethod(), request.getRequestURI(), message);
        return ApiResponse.validationError(message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Object> handleException(Exception ex, HttpServletRequest request) {
        // traceId 只认 MDC 那一个（TraceIdFilter 写的）：日志、响应头、响应体三处必须同一个 id
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        log.error("Unhandled exception traceId={}", traceId, ex);
        String message = i18nUtil.translate(GENERIC_ERROR_MSG);
        return ApiResponse.internalError(message);
    }

}
