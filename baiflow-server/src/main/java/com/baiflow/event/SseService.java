package com.baiflow.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 事件服务 — 维护"用户 → 连接"的注册表，向指定用户的在线连接推送事件。
 * <p>
 * 每个用户可有多条连接（多个浏览器标签）。连接在完成/超时/异常时自动清理；
 * 定时心跳发送注释行保活并清理失效连接。
 * <p>
 * 事件数据直接传对象，由 Spring 的 HttpMessageConverter 序列化为 JSON。
 */
@Slf4j
@Service
public class SseService {

    /** 注册表：userId → 该用户的活跃连接列表 */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> registry =
            new ConcurrentHashMap<>();

    /** 连接超时：1 小时；失效连接由心跳发送失败时清理（避免频繁断连重连） */
    private static final long EMITTER_TIMEOUT_MS = 3_600_000L;

    /**
     * 为指定用户注册一个 SSE 连接。
     *
     * @return 可直接返回给客户端的 SseEmitter
     */
    public SseEmitter register(String userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> list =
                registry.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        return emitter;
    }

    /**
     * 向指定用户的全部在线连接推送一个事件。
     *
     * @param userId    目标用户
     * @param eventType 事件类型（发送时用枚举名作为事件名）
     * @param data      事件数据（序列化为 JSON）
     */
    public void publish(String userId, SseEventType eventType, Object data) {
        List<SseEmitter> emitters = registry.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventType.name()).data(data));
            } catch (Exception e) {
                log.debug("SSE 推送失败，移除连接: userId={}, event={}, err={}",
                        userId, eventType, e.getMessage());
                remove(userId, emitter);
            }
        }
    }

    private void remove(String userId, SseEmitter emitter) {
        List<SseEmitter> list = registry.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                registry.remove(userId, list);
            }
        }
    }

    /** 心跳：向所有连接发送注释行保活并清理失效连接。由 {@link SseHeartbeatScheduler} 定时调用。 */
    public void heartbeat() {
        registry.forEach((userId, emitters) -> emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                remove(userId, emitter);
            }
        }));
    }
}
