package com.baiflow.auth.constant;

/**
 * 登录锁定相关的 Redis 键前缀 — 供认证服务、定时任务与用户管理共享。
 * <p>键均以用户名后缀，如 {@code login:lock:<username>}。
 */
public final class LoginLockRedisKeys {

    private LoginLockRedisKeys() {}

    /** 登录失败次数（String 值，滑动窗口内失效） */
    public static final String FAIL_COUNT = "login:fail:";

    /** 登录锁定标记（String 值 "1"，TTL 到期即解锁） */
    public static final String LOCK = "login:lock:";
}
