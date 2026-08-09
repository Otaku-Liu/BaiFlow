package com.baiflow.auth.security;

import com.baiflow.auth.config.BaiflowProperties;
import com.baiflow.auth.entity.AuthSession;
import com.baiflow.auth.service.AuthSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 会话 token 服务 — 签发（随机 256-bit，只存 SHA-256 哈希）、校验、吊销、滑动续期。
 * <p>
 * 认证模型 2：登录建一条会话，每次请求按 token 哈希查会话校验；吊销即硬删会话记录（即时生效），
 * 历史由审计日志留痕（见 {@code AuthServiceImpl} 的 LOGOUT / FORCE_LOGOUT / PASSWORD_CHANGED）。
 * ANDROID / WEB 会话均**滑动续期**（活跃请求顺延到 now + 对应时长，不活跃自动失效：ANDROID 180 天 / WEB webHours）。
 */
@Service
public class SessionTokenService {

    /** 会话 token 随机长度：32 字节（256-bit） */
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 滑动续期写库节流：距上次续期超过 1 小时才更新（ANDROID / WEB 通用） */
    private static final Duration TOUCH_INTERVAL = Duration.ofHours(1);

    @Autowired
    private AuthSessionService authSessionService;
    @Autowired
    private BaiflowProperties properties;

    /** 新建会话的结果：明文 token（仅此一次返回给客户端）+ 会话 ID + 到期时间 */
    public record CreatedSession(String token, String sessionId, LocalDateTime expiresAt) {
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
    public CreatedSession create(String userId, String deviceType, String deviceName,
                                 String ip, String userAgent) {
        String token = generateToken();
        AuthSession session = new AuthSession();
        session.setUserId(userId);
        session.setDeviceType("ANDROID".equals(deviceType) ? "ANDROID" : "WEB");
        session.setDeviceName(deviceName != null ? deviceName : "");
        session.setIp(ip != null ? ip : "");
        session.setUserAgent(userAgent != null ? userAgent : "");
        session.setTokenHash(sha256Hex(token));
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plus(expiryFor(session.getDeviceType())));
        authSessionService.save(session);
        return new CreatedSession(token, session.getId(), session.getExpiresAt());
    }

    /** 按明文 token 查会话（内部先哈希） */
    public AuthSession findByToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return authSessionService.getOne(new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getTokenHash, sha256Hex(token))
                .last("LIMIT 1"));
    }

    /**
     * 吊销某用户全部会话，保留指定会话 — 硬删记录。
     * <p>单个会话的吊销（登出/强制下线）由调用方直接删除记录，审计留痕见 {@code AuthServiceImpl}。
     *
     * @param keepSessionId 保留的会话 ID（可空 = 全部吊销）
     */
    public void revokeAllExcept(String userId, String keepSessionId) {
        LambdaQueryWrapper<AuthSession> wrapper = new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getUserId, userId);
        if (keepSessionId != null && !keepSessionId.isEmpty()) {
            wrapper.ne(AuthSession::getId, keepSessionId);
        }
        authSessionService.remove(wrapper);
    }

    /** 滑动续期：更新 last_used_at 并把 expires_at 顺延到 now + 对应设备类型时长（ANDROID 180 天 / WEB webHours） */
    public void touch(AuthSession session) {
        LocalDateTime now = LocalDateTime.now();
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plus(expiryFor(session.getDeviceType())));
        authSessionService.updateById(session);
    }

    /** 滑动续期写库节流间隔 */
    public Duration touchInterval() {
        return TOUCH_INTERVAL;
    }

    private Duration expiryFor(String deviceType) {
        if ("ANDROID".equals(deviceType)) {
            return Duration.ofDays(properties.getAuthSession().getAndroidDays());
        }
        return Duration.ofHours(properties.getAuthSession().getWebHours());
    }

    private static String generateToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** token → SHA-256 十六进制（入库/查询用，绝不落明文） */
    public static String sha256Hex(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
