package com.baiflow.schedule;

import com.baiflow.audit.service.BfAuditLogService;
import com.baiflow.auth.constant.LoginLockRedisKeys;
import com.baiflow.user.entity.BfUser;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.BfUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 使锁键与用户状态保持同生命周期。Redis 不可用时保守跳过（fail-closed），避免误解锁。
 */
@Slf4j
@Component
public class LoginLockScheduler {

    @Autowired
    private BfUserMapper userMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private BfAuditLogService auditService;

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
                // 锁键不存在即代表锁定已到期；条件更新（WHERE status=LOCKED）保证多实例并发扫描时仅首个生效
                if (!redisTemplate.hasKey(LoginLockRedisKeys.LOCK + user.getUsername())) {
                    int updated = userMapper.update(null, new LambdaUpdateWrapper<BfUser>()
                            .eq(BfUser::getId, user.getId())
                            .eq(BfUser::getStatus, UserStatus.LOCKED)
                            .set(BfUser::getStatus, UserStatus.NORMAL));
                    if (updated <= 0) {
                        continue;
                    }
                    auditService.log(user.getId(), "ACCOUNT_UNLOCKED", "USER", user.getId(),
                            null, null, "登录锁定到期，账号自动恢复为正常");
                    log.info("登录锁定到期，账号恢复为 NORMAL: userId={}, username={}",
                            user.getId(), user.getUsername());
                    restored++;
                }
            } catch (DataAccessException e) {
                // Redis 不可用时跳过（fail-closed），等待下一轮再判定
                log.warn("Redis 不可用，跳过登录锁定到期恢复: userId={}, error={}",
                        user.getId(), e.getMessage());
            }
        }

        if (restored > 0) {
            log.info("本轮已自动恢复 {} 个锁定账号", restored);
        }
    }
}
