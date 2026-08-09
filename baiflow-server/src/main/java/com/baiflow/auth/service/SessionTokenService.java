package com.baiflow.auth.service;

import com.baiflow.auth.entity.AuthSession;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 会话 token 服务接口 — 签发（随机 256-bit，只存 SHA-256 哈希）、校验、吊销、滑动续期。
 * <p>
 * 认证模型 2：登录建一条会话，每次请求按 token 哈希查会话校验；吊销即硬删会话记录（即时生效），
 * 历史由审计日志留痕（见 {@code AuthServiceImpl} 的 LOGOUT / FORCE_LOGOUT / PASSWORD_CHANGED）。
 * ANDROID / WEB 会话均**滑动续期**（活跃请求顺延到 now + 对应时长，不活跃自动失效：ANDROID 180 天 / WEB webHours）。
 */
public interface SessionTokenService {

    /** 新建会话的结果：明文 token（仅此一次返回给客户端）+ 会话 ID + 到期时间 */
    record CreatedSession(String token, String sessionId, LocalDateTime expiresAt) {
    }

    /**
     * 签发会话。
     *
     * @param userId     用户 ID
     * @param deviceType 设备类型（ANDROID / WEB）
     * @param deviceName 设备名（可空，空则后续按 UA 推导）
     * @param ip         登录 IP
     * @param userAgent  登录 User-Agent
     * @return 明文 token（客户端保存）与元信息
     */
    CreatedSession create(String userId, String deviceType, String deviceName, String ip, String userAgent);

    /**
     * 按明文 token 查会话（内部先哈希）。
     */
    AuthSession findByToken(String token);

    /**
     * 吊销某用户全部会话，保留指定会话 — 硬删记录。
     * <p>单个会话的吊销（登出/强制下线）由调用方直接删除记录，审计留痕见 {@code AuthServiceImpl}。
     *
     * @param keepSessionId 保留的会话 ID（可空 = 全部吊销）
     */
    void revokeAllExcept(String userId, String keepSessionId);

    /**
     * 滑动续期：更新 last_used_at 并把 expires_at 顺延到 now + 对应设备类型时长（ANDROID 180 天 / WEB webHours）。
     */
    void touch(AuthSession session);

    /** 滑动续期写库节流间隔 */
    Duration touchInterval();
}
