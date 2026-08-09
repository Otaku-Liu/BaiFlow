package com.baiflow.auth.dto.response;

import java.time.LocalDateTime;

/**
 * 用户登录设备信息（设备历史 + 在线状态）。
 *
 * @param online          是否在线（当前存在未过期会话）
 * @param current         是否当前请求所在设备
 * @param activeSessionId 在线时的活跃会话 ID（供强制下线，离线为 null）
 */
public record UserDeviceInfo(String deviceName, String deviceType, LocalDateTime firstLoginAt,
                             LocalDateTime lastLoginAt, LocalDateTime lastActiveAt,
                             boolean online, boolean current, String activeSessionId) {
}
