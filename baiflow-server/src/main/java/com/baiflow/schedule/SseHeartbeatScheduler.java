package com.baiflow.schedule;

import com.baiflow.event.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SSE 心跳定时任务 — 每 30 秒向所有在线连接发送注释行保活，并清理已失效的连接。
 */
@Slf4j
@Component
public class SseHeartbeatScheduler {

    @Autowired
    private SseService sseService;

    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        sseService.heartbeat();
    }
}
