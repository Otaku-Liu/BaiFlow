package com.baiflow.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * HTTP 请求/响应日志过滤器 — 记录每个请求的方法、URI、状态码和耗时。
 */
@Slf4j
@Component
public class HttpLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();

        // 包装响应以捕获状态码
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, responseWrapper);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            int status = responseWrapper.getStatus();
            String path = query != null ? uri + "?" + query : uri;
            log.info("{} {} → {} ({}ms)", method, path, status, elapsed);
            responseWrapper.copyBodyToResponse();
        }
    }
}
