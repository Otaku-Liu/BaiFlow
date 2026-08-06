package com.baiflow.auth.dto.response;

import com.baiflow.user.dto.response.UserInfo;

import java.time.LocalDateTime;

/**
 * 登录响应 — 会话 token（长会话，服务端逐请求校验）+ 会话元信息 + 用户信息。
 */
public record LoginResponse(String token, String sessionId, LocalDateTime expiresAt, UserInfo user) {
}
