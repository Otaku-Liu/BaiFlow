package com.baiflow.auth.service;

import com.baiflow.audit.constant.AuditAction;
import com.baiflow.audit.service.BfAuditLogService;
import com.baiflow.auth.constant.LoginLockRedisKeys;
import com.baiflow.user.entity.BfUser;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.BfUserMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 登录锁的清除与恢复 —— 清除锁键、LOCKED→NORMAL 状态恢复的唯一入口。
 * <p>
 * 范围仅此二者：「<b>加锁</b>」不在这里 —— 失败计数与置锁键仍由 {@code AuthServiceImpl.recordFailure}
 * 负责，因为它与「密码校验失败」的业务判定绑在一起，拆过来反而要跨类回传判定结果。
 * <p>
 * 判定「锁键是否还在」用 {@link RedisLockKeyReader}。此前这两处写操作分别散落在认证服务、
 * 用户服务与定时任务中，其中「恢复为 NORMAL」在认证服务的登录兜底路径与定时任务里
 * 各写了一份逐字相同的实现，现已合一。
 */
@Slf4j
@Service
public class LoginLockService {

    @Autowired
    private BfUserMapper userMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private BfAuditLogService auditService;

    /**
     * 清除登录锁定（锁键 + 失败计数），如登录成功、管理员改动锁定用户的状态时调用。
     * <p>Redis 不可用时降级跳过（锁键本身会随 TTL 到期，不影响状态变更）。
     * 日志由调用方按自己的语境打 —— 两处调用中只有用户管理那处需要 info 级记录。
     */
    public void clearLoginLock(String username) {
        try {
            redisTemplate.delete(LoginLockRedisKeys.LOCK + username);
            redisTemplate.delete(LoginLockRedisKeys.FAIL_COUNT + username);
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，跳过清除登录锁定: username={}, error={}", username, e.getMessage());
        }
    }

    /**
     * 将用户状态从 LOCKED 条件恢复为 NORMAL（幂等），并记录审计日志。
     * <p>使用条件更新（WHERE status=LOCKED）：多实例并发扫描时仅首个实例生效，避免重复审计。
     * @param user 目标用户
     * @param ip   触发方 IP（定时任务无请求上下文时传 null）
     * @param ua   触发方 User-Agent（同上）
     * @return 本次是否真的恢复了（未更新到任何行时返回 false）
     */
    public boolean restore(BfUser user, String ip, String ua) {
        int updated = userMapper.update(null, new LambdaUpdateWrapper<BfUser>()
                .eq(BfUser::getId, user.getId())
                .eq(BfUser::getStatus, UserStatus.LOCKED)
                .set(BfUser::getStatus, UserStatus.NORMAL));
        if (updated <= 0) {
            return false;
        }
        user.setStatus(UserStatus.NORMAL);
        auditService.log(user.getId(), AuditAction.ACCOUNT_UNLOCKED,
                BfAuditLogService.AuditTarget.user(user.getId()), ip, ua,
                "登录锁定到期，账号自动恢复为正常");
        log.info("登录锁定到期，账号恢复为 NORMAL: userId={}, username={}", user.getId(), user.getUsername());
        return true;
    }
}
