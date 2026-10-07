package com.baiflow.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路 id：入站带 {@code X-Trace-Id} 就沿用（便于多端/多服务对齐同一次操作），否则生成一个；
 * 写入 MDC 供日志 pattern 与 {@code ApiResponse} 取用，并回写响应头。
 *
 * <p><b>注册为最高优先级</b>：要让鉴权过滤器的查询日志、以及后面每条 SQL 的计时日志都带上它
 * （{@code HttpLoggingFilter} 是无显式顺序的 @Component，跑在 Spring Security 之后）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 请求/响应头名 */
    public static final String HEADER = "X-Trace-Id";
    /** MDC 键名，与 logback pattern 里的 %X{traceId} 对应 */
    public static final String MDC_KEY = "traceId";

    /** 沿用外部 id 的长度上限：别让调用方塞超长值把日志撑坏 */
    private static final int MAX_LEN = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank() || traceId.length() > MAX_LEN) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程池复用，必须清掉，否则下一个请求会继承上一个的 id
            MDC.remove(MDC_KEY);
        }
    }
}
