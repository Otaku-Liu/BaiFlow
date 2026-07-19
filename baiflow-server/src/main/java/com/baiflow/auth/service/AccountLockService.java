package com.baiflow.auth.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限制服务 — 5 次失败后锁定 15 分钟。
 * <p>
 * 基于内存的简单实现，用于 MVP 阶段。
 * 后续可改为 Redis 实现以支持多实例部署。
 */
@Service
public class AccountLockService {
    /** 最大失败次数 */
    private static final int MAX_FAILURES = 5;
    /** 锁定时长（分钟） */
    private static final int LOCK_MINUTES = 15;

    /** key: username, value: 失败次数 */
    private final Map<String, Integer> failureCount = new ConcurrentHashMap<>();
    /** key: username, value: 锁定截止时间（epoch milliseconds）*/
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();

    /** 检查账户是否被锁定 */
    public boolean isLocked(String username) {
        Long until = lockedUntil.get(username);
        if (until == null) { return false; }
        if (System.currentTimeMillis() >= until) {
            lockedUntil.remove(username);
            return false;
        }
        return true;
    }

    /** 记录登录失败 */
    public void recordFailure(String username) {
        int count = failureCount.getOrDefault(username, 0) + 1;
        failureCount.put(username, count);
        if (count >= MAX_FAILURES) {
            lockedUntil.put(username, System.currentTimeMillis() + LOCK_MINUTES * 60_000L);
        }
    }

    /** 登录成功时清除失败记录 */
    public void clearFailures(String username) {
        failureCount.remove(username);
        lockedUntil.remove(username);
    }

    /** 获取剩余失败次数 */
    public int remainingAttempts(String username) {
        return MAX_FAILURES - failureCount.getOrDefault(username, 0);
    }
}
