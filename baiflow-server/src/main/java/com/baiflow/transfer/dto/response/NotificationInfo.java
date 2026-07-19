package com.baiflow.transfer.dto.response;

import com.baiflow.transfer.entity.Notification;
import com.baiflow.transfer.enums.NotificationLevel;
import com.baiflow.transfer.enums.ReadStatus;

import java.time.LocalDateTime;

/**
 * 通知响应 DTO。
 */
public record NotificationInfo(
        String id,
        String userId,
        NotificationLevel level,
        String title,
        String content,
        ReadStatus readStatus,
        LocalDateTime createdAt,
        LocalDateTime readAt) {

    public static NotificationInfo from(Notification n) {
        return new NotificationInfo(n.getId(), n.getUserId(), n.getLevel(),
                n.getTitle(), n.getContent(), n.getReadStatus(),
                n.getCreatedAt(), n.getReadAt());
    }
}
