package com.baiflow.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求元信息工具 — 从当前请求上下文取客户端 IP / User-Agent。
 */
public final class RequestUtil {

    private RequestUtil() {
    }

    /** 客户端 IP：优先 X-Forwarded-For 首个值（反代场景），否则 RemoteAddr */
    public static String getClientIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    /** 客户端 User-Agent */
    public static String getClientUserAgent() {
        return getHeader("User-Agent");
    }

    /** 读取当前请求的指定请求头（不存在返回空串） */
    public static String getHeader(String name) {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String v = attrs.getRequest().getHeader(name);
                return v != null ? v : "";
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
