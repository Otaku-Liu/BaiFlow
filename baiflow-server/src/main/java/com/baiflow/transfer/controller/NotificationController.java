package com.baiflow.transfer.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.transfer.dto.response.NotificationInfo;
import com.baiflow.transfer.service.NotificationService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通知接口控制器。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 分页查询当前用户的通知，支持按阅读状态筛选。
     */
    @GetMapping
    public ApiResponse<IPage<NotificationInfo>> list(
            @RequestParam(required = false) String readStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ApiResponse.success(
                notificationService.listNotifications(auth.getPrincipal().toString(), readStatus, page, size));
    }

    /**
     * 获取当前用户未读通知数量。
     */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(Authentication auth) {
        long count = notificationService.countUnread(auth.getPrincipal().toString());
        return ApiResponse.success(Map.of("count", count));
    }

    /**
     * 将指定通知标记为已读。
     */
    @PatchMapping("/{id}/read")
    public ApiResponse<Map<String, Object>> markRead(@PathVariable String id, Authentication auth) {
        notificationService.markAsRead(id, auth.getPrincipal().toString());
        return ApiResponse.success(Map.of("result", "已标记为已读"));
    }
}
