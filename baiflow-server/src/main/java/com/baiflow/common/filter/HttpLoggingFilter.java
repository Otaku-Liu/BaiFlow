package com.baiflow.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP 请求/响应详细日志过滤器。
 * <p>
 * 记录每次调用的请求方式、完整 URL、请求头、请求参数、请求体和响应状态、响应头、
 * 响应体及耗时。敏感头（Authorization、Cookie、Set-Cookie）自动脱敏。
 */
@Slf4j
@Component
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            logRequest(requestWrapper);
            logResponse(responseWrapper, elapsed);
            responseWrapper.copyBodyToResponse();
        }
    }

    // ---- 请求日志 ----

    private void logRequest(ContentCachingRequestWrapper request) {
        String method = request.getMethod();
        String fullUrl = buildFullUrl(request);
        Map<String, String> headers = filterSensitive(requestHeaders(request));
        String queryParams = request.getQueryString();
        String body = getRequestBody(request);

        log.info("HTTP 请求 → {} {}\n  请求头 : {}\n  查询参数: {}\n  请求体 : {}",
                method, fullUrl,
                headers.isEmpty() ? "(无)" : headers,
                queryParams != null ? queryParams : "(无)",
                !body.isEmpty() ? body : "(无)");
    }

    // ---- 响应日志 ----

    private void logResponse(ContentCachingResponseWrapper response, long elapsedMs) {
        int status = response.getStatus();
        Map<String, String> headers = filterSensitive(responseHeaders(response));
        String body = getResponseBody(response);

        log.info("HTTP 响应 ← {} ({}ms)\n  响应头: {}\n  响应体: {}",
                status, elapsedMs,
                headers.isEmpty() ? "(无)" : headers,
                !body.isEmpty() ? body : "(无)");
    }

    // ---- 辅助方法 ----

    private String buildFullUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String uri = request.getRequestURI();
        String query = request.getQueryString();

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(host);
        if (port != 80 && port != 443) {
            url.append(":").append(port);
        }
        url.append(uri);
        if (query != null) {
            url.append("?").append(query);
        }
        return url.toString();
    }

    private Map<String, String> requestHeaders(HttpServletRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                map.put(name, request.getHeader(name));
            }
        }
        return map;
    }

    private Map<String, String> responseHeaders(HttpServletResponse response) {
        Map<String, String> map = new LinkedHashMap<>();
        Collection<String> names = response.getHeaderNames();
        for (String name : names) {
            map.put(name, response.getHeader(name));
        }
        return map;
    }

    private Map<String, String> filterSensitive(Map<String, String> headers) {
        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (SENSITIVE_HEADERS.contains(e.getKey().toLowerCase())) {
                filtered.put(e.getKey(), "***");
            } else {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        return filtered;
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) { return ""; }
        return new String(content, StandardCharsets.UTF_8).replace("\n", " ").trim();
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0) { return ""; }
        // 截断过长响应体，防止日志膨胀
        String body = new String(content, StandardCharsets.UTF_8);
        if (body.length() > 1024) {
            body = body.substring(0, 1024) + "...(截断)";
        }
        return body.replace("\n", " ").trim();
    }
}
