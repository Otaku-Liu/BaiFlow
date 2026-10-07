package com.baiflow.schedule;

import com.baiflow.auth.constant.LoginLockRedisKeys;
import com.baiflow.user.entity.BfUser;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.BfUserMapper;
import com.baiflow.user.service.BfUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 登录锁定到期恢复任务 — 定期扫描状态为 LOCKED 的用户。
 * <p>
 * 登录失败达到阈值时（见 {@code AuthServiceImpl.recordFailure}），用户状态被持久化为 LOCKED，
 * 同时写入 Redis 锁键 {@code login:lock:<username>}（TTL = 锁定时长）。本任务每 60 秒扫描一次，
 * 对「状态=LOCKED 且 Redis 锁键已消失（到期）」的用户恢复为 NORMAL，
 * 使锁键与用户状态保持同生命周期。锁键状态未知（Redis 不可用）时保守跳过，避免误解锁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLockScheduler {

    private final BfUserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final BfUserService userService;

    @Scheduled(fixedRate = 60_000)
    public void restoreExpiredLocks() {
        List<BfUser> lockedUsers = userMapper.selectList(
                new LambdaQueryWrapper<BfUser>().eq(BfUser::getStatus, UserStatus.LOCKED));
        if (lockedUsers == null || lockedUsers.isEmpty()) {
            return;
        }

        int restored = 0;
        for (BfUser user : lockedUsers) {
            try {
                // 仅确证锁键已消失（锁定到期）才恢复；Redis 不可用时维持锁定，等待下一轮再判定
                boolean lockKeyGone = false;
                try {
                    lockKeyGone = Boolean.FALSE.equals(redisTemplate.hasKey(LoginLockRedisKeys.LOCK + user.getUsername()));
                } catch (DataAccessException e) {
                    log.warn("Redis 不可用，本轮跳过该用户: userId={}, error={}", user.getId(), e.getMessage());
                }
                // 恢复走 BfUserService（条件更新 + 审计，多实例并发时仅首个生效）
                if (lockKeyGone && userService.restoreLockedUser(user, null, null)) {
                    restored++;
                }
            } catch (DataAccessException e) {
                // 数据库操作失败：跳过该用户，等待下一轮再判定
                log.warn("锁定到期恢复失败，跳过该用户: userId={}, error={}", user.getId(), e.getMessage());
            }
        }

        if (restored > 0) {
            log.info("本轮已自动恢复 {} 个锁定账号", restored);
        }
    }
}
