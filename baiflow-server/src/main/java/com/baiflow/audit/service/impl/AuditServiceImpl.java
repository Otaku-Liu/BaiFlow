package com.baiflow.audit.service.impl;

import com.baiflow.audit.entity.AuditLog;
import com.baiflow.audit.mapper.AuditLogMapper;
import com.baiflow.audit.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditServiceImpl implements AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);
    private final AuditLogMapper mapper;

    public AuditServiceImpl(AuditLogMapper mapper) { this.mapper = mapper; }

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
}
