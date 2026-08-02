package com.baiflow.audit.service.impl;

import com.baiflow.audit.dto.response.LoginLogVO;
import com.baiflow.audit.entity.AuditLog;
import com.baiflow.audit.mapper.AuditLogMapper;
import com.baiflow.audit.service.AuditService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuditServiceImpl implements AuditService {
    @Autowired
    private AuditLogMapper mapper;

    @Override @Async
    public void log(String actorUserId, String action, String targetType, String targetId,
                    String ipAddress, String userAgent, String detail) {
        AuditLog entry = new AuditLog();
        entry.setActorUserId(actorUserId != null ? actorUserId : "");
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setIpAddress(ipAddress != null ? ipAddress : "");
        entry.setUserAgent(userAgent != null ? userAgent : "");
        entry.setDetail(detail != null ? detail : "");
        mapper.insert(entry);
        log.debug("审计日志: action={}, target={}.{}", action, targetType, targetId);
    }

    @Override
    public Page<LoginLogVO> queryLoginLogs(int page, int size, String username, String status,
                                           String startDate, String endDate) {
        Page<LoginLogVO> p = new Page<>(page, size);
        return mapper.selectLoginLogs(p, username, status, startDate, endDate);
    }
}
