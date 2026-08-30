package com.baiflow.user.dto.response;

import com.baiflow.user.entity.BfUser;
import com.baiflow.user.enums.UserRole;
import com.baiflow.user.enums.UserStatus;

import java.time.LocalDateTime;

public record UserInfo(String id, String username, String displayName, String avatarUrl,
                       UserRole role, UserStatus status, LocalDateTime lastLoginAt, LocalDateTime createdAt) {
    public static UserInfo from(BfUser u) {
        return new UserInfo(u.getId(), u.getUsername(), u.getDisplayName(), u.getAvatarUrl(),
                u.getRole(), u.getStatus(), u.getLastLoginAt(), u.getCreatedAt());
    }
}
