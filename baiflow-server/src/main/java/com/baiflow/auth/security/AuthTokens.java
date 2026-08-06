package com.baiflow.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/**
 * 会话 token 提取 — 支持 {@code Authorization: Bearer <token>} 头与
 * {@code ?token=} 查询参数（后者供浏览器直接请求的 {@code <img>/<video>}/SSE 场景）。
 */
public final class AuthTokens {

    private AuthTokens() {
    }

    /** 从请求中提取会话 token；无则返回 null */
    public static String extract(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        return null;
    }
}
