package com.baiflow.user.enums;

/**
 * 账号状态 — 仅表达人工管理的账号生命周期，不含任何自动失效的临时状态。
 * <p>登录失败锁定是带 TTL 的运行时防护，不落库、不进本枚举：权威状态只在 Redis 锁键
 * {@code login:lock:<username>} 里（见 {@code docs/02-database.md} Redis 键表）。
 */
public enum UserStatus { NORMAL, DISABLED }
