package com.baiflow.audit.service.impl;

import com.baiflow.audit.dto.response.LoginLogVO;
import com.baiflow.audit.entity.BfAuditLog;
import com.baiflow.audit.mapper.BfAuditLogMapper;
import com.baiflow.audit.service.BfAuditLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BfAuditLogServiceImpl implements BfAuditLogService {
    @Autowired
    private BfAuditLogMapper mapper;

    @Override @Async
    public void log(String actorUserId, String action, String targetType, String targetId,
                    String ipAddress, String userAgent, String detail) {
        BfAuditLog entry = new BfAuditLog();
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
