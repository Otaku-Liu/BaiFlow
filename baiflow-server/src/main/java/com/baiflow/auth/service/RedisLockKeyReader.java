package com.baiflow.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis 锁键状态查询 —— 「某个键族的锁键是否还在」这一判定的唯一入口。
 * <p>
 * 只回答「键的状态」，不回答「要不要放行」：Redis 不可用时返回 {@link LockKeyState#UNKNOWN}，
 * 由调用点按自身现状决定策略。各调用点共同遵循一条规则：<b>不确定时不改变当前状态</b>。
 * <ul>
 *   <li>前置检查（此刻现状为「未锁」）：仅 {@code PRESENT} 拦截，{@code UNKNOWN} 放行 —— Redis 故障不阻断请求</li>
 *   <li>{@code LOCKED} 兜底判定与定时任务（此刻现状为「已锁」）：仅 {@code ABSENT} 解除，
 *       {@code UNKNOWN} 维持 —— 解除锁定必须确证</li>
 * </ul>
 * 目前服务两个键族：登录锁（{@code login:lock:}）与分享提取码锁（{@code share:code:lock:}）。
 * 锁键的<b>写</b>操作不在此处：登录锁见 {@link LoginLockService}。
 * <p>
 * 与 {@code docs/06} 那条「不为 Redis 降级块抽跨键族的通用包装类」的边界：本类<b>不</b>属于该规则所禁的
 * 通用包装 —— 共享的是锁键状态语义（三态，把各调用点原先 fail-open / fail-closed 的分歧显式化），
 * 而不是那三行 catch；{@code stateOf} 的 try/catch 是「探测键状态」这一动作本身的必要边界。
 */
@Slf4j
@Service
public class RedisLockKeyReader {

    /** 锁键状态：PRESENT 在锁窗口内 · ABSENT 确认无锁 · UNKNOWN Redis 不可用、状态未知 */
    public enum LockKeyState { PRESENT, ABSENT, UNKNOWN }

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 查询指定键族下某个 ID 的锁键状态。
     * @param keyPrefix 锁键前缀（含分隔符），如 {@code LoginLockRedisKeys.LOCK}
     * @param id        业务 ID（用户名 / 分享 ID 等）
     * @return 锁键状态；Redis 不可用时返回 {@link LockKeyState#UNKNOWN}，不抛异常
     */
    public LockKeyState stateOf(String keyPrefix, String id) {
        try {
            return redisTemplate.hasKey(keyPrefix + id)
                    ? LockKeyState.PRESENT : LockKeyState.ABSENT;
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，锁键状态未知: key={}, error={}", keyPrefix + id, e.getMessage());
            return LockKeyState.UNKNOWN;
        }
    }
}
