package com.baiflow.transfer.service;

import com.baiflow.transfer.dto.response.NotificationInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 通知服务接口 — 管理用户级别的系统通知。
 */
public interface NotificationService {

    /**
     * 分页查询当前用户的通知，支持按阅读状态筛选。
     *
     * @param userId     当前用户 ID
     * @param readStatus 阅读状态筛选（可选），传 null 表示全部
     * @param page       页码（从 1 开始）
     * @param size       每页数量
     * @return 分页通知列表
     */
    IPage<NotificationInfo> listNotifications(String userId, String readStatus, int page, int size);

    /**
     * 获取当前用户的未读通知数量。
     */
    long countUnread(String userId);

    /**
     * 将指定通知标记为已读。仅通知所有者可操作。
     *
     * @param notificationId 通知 ID
     * @param userId         当前用户 ID
     */
    void markAsRead(String notificationId, String userId);

    /**
     * 创建一条新通知。
     *
     * @param userId  目标用户 ID
     * @param level   通知级别（INFO / WARN / ERROR）
     * @param title   标题
     * @param content 正文
     * @return 创建的通知信息
     */
    NotificationInfo createNotification(String userId, String level, String title, String content);
}
