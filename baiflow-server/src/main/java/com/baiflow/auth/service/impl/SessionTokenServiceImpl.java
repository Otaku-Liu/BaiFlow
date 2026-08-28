package com.baiflow.auth.service.impl;

import com.baiflow.auth.config.BaiflowProperties;
import com.baiflow.auth.entity.AuthSession;
import com.baiflow.auth.service.SessionTokenService;
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
 * 会话 token 服务实现 — 见 {@link SessionTokenService} 接口说明。
 */
@Service
public class SessionTokenServiceImpl implements SessionTokenService {

    /** 会话 token 随机长度：32 字节（256-bit） */
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 滑动续期写库节流：距上次续期超过 1 小时才更新（ANDROID / WEB 通用） */
    private static final Duration TOUCH_INTERVAL = Duration.ofHours(1);

    @Autowired
    private AuthSessionService authSessionService;
    @Autowired
    private BaiflowProperties properties;

    @Override
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
        // 登录即清理该用户已过期的历史会话，控制表体积（走 idx_user 索引，无需定时任务）
        authSessionService.remove(new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getUserId, userId)
                .lt(AuthSession::getExpiresAt, now));
        authSessionService.save(session);
        return new CreatedSession(token, session.getId(), session.getExpiresAt());
    }

    @Override
    public AuthSession findByToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return authSessionService.getOne(new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getTokenHash, sha256Hex(token))
                .last("LIMIT 1"));
    }

    @Override
    public void revokeAllExcept(String userId, String keepSessionId) {
        LambdaQueryWrapper<AuthSession> wrapper = new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getUserId, userId);
        if (keepSessionId != null && !keepSessionId.isEmpty()) {
            wrapper.ne(AuthSession::getId, keepSessionId);
        }
        authSessionService.remove(wrapper);
    }

    @Override
    public void touch(AuthSession session) {
        LocalDateTime now = LocalDateTime.now();
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plus(expiryFor(session.getDeviceType())));
        authSessionService.updateById(session);
    }

    @Override
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
    private static String sha256Hex(String token) {
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
