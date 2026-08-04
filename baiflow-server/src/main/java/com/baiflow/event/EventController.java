package com.baiflow.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 事件接口 — 长连接实时推送。
 * <p>
 * 需登录（JWT）。浏览器 EventSource 无法带 Authorization 头，
 * 使用 {@code /api/events?token=<jwt>} 查询参数鉴权（后端 JwtAuthenticationFilter 已支持）。
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private SseService sseService;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth) {
        return sseService.register(auth.getPrincipal().toString());
    }
}
