package com.baiflow.schedule;

import com.baiflow.event.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SSE 心跳定时任务 — 每 30 秒向所有在线连接发送注释行保活，并清理已失效的连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final SseService sseService;

    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        sseService.heartbeat();
    }
}
