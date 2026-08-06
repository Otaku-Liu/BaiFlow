package com.baiflow.auth.dto.response;

import com.baiflow.auth.entity.AuthSession;

import java.time.LocalDateTime;

/**
 * 登录会话信息（设备管理列表项）。
 *
 * @param current 是否当前请求所在会话
 */
public record AuthSessionInfo(String id, String deviceName, String deviceType, String ip,
                              LocalDateTime lastUsedAt, LocalDateTime createdAt, boolean current) {
    public static AuthSessionInfo from(AuthSession s, boolean current) {
        return new AuthSessionInfo(s.getId(), s.getDeviceName(), s.getDeviceType(), s.getIp(),
                s.getLastUsedAt(), s.getCreatedAt(), current);
    }
}
