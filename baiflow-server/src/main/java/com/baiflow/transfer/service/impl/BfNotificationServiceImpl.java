package com.baiflow.transfer.service.impl;

import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.transfer.dto.response.NotificationInfo;
import com.baiflow.transfer.entity.BfNotification;
import com.baiflow.transfer.enums.NotificationLevel;
import com.baiflow.transfer.enums.ReadStatus;
import com.baiflow.transfer.mapper.BfNotificationMapper;
import com.baiflow.transfer.service.BfNotificationService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知服务实现。
 */
@Service
public class BfNotificationServiceImpl extends ServiceImpl<BfNotificationMapper, BfNotification> implements BfNotificationService {

    @Override
    public IPage<NotificationInfo> listNotifications(String userId, String readStatus, int page, int size) {
        List<BfNotification> all = lambdaQuery()
                .eq(BfNotification::getUserId, userId)
                .eq(readStatus != null && !readStatus.isBlank(), BfNotification::getReadStatus, readStatus)
                .orderByDesc(BfNotification::getCreatedAt)
                .list();
        int total = all.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<NotificationInfo> recs = (from < total ? all.subList(from, to) : List.<BfNotification>of())
                .stream().map(NotificationInfo::from).toList();
        IPage<NotificationInfo> r = new Page<>(page, size, total);
        r.setRecords(recs);
        return r;
    }

    @Override
    public long countUnread(String userId) {
        return lambdaQuery()
                .eq(BfNotification::getUserId, userId)
                .eq(BfNotification::getReadStatus, "UNREAD")
                .count();
    }

    @Override
    public void markAsRead(String notificationId, String userId) {
        BfNotification n = getById(notificationId);
        if (n == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在"); }
        // 校验通知归属
        if (!n.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此通知");
        }
        // 已读通知无需重复标记
        if (n.getReadStatus() == ReadStatus.READ) { return; }
        n.setReadStatus(ReadStatus.READ);
        n.setReadAt(LocalDateTime.now());
        updateById(n);
    }

    @Override
    public NotificationInfo createNotification(String userId, String level, String title, String content) {
        BfNotification n = new BfNotification();
        n.setUserId(userId);
        n.setLevel(NotificationLevel.valueOf(level));
        n.setTitle(title);
        n.setContent(content);
        n.setReadStatus(ReadStatus.UNREAD);
        save(n);
        return NotificationInfo.from(n);
    }
}
