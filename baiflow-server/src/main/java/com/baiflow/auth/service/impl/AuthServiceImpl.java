package com.baiflow.auth.service.impl;

import com.baiflow.auth.constant.LoginLockRedisKeys;
import com.baiflow.auth.dto.request.LoginRequest;
import com.baiflow.auth.dto.response.AuthSessionInfo;
import com.baiflow.auth.dto.response.LoginResponse;
import com.baiflow.auth.dto.response.UserDeviceInfo;
import com.baiflow.auth.entity.BfAuthSession;
import com.baiflow.auth.entity.BfUserDevice;
import com.baiflow.auth.mapper.BfAuthSessionMapper;
import com.baiflow.auth.service.SessionTokenService;
import com.baiflow.auth.service.AuthService;
import com.baiflow.auth.service.BfUserDeviceService;
import com.baiflow.auth.security.SecurityUtils;
import com.baiflow.audit.constant.AuditAction;
import com.baiflow.audit.constant.AuditTargetType;
import com.baiflow.audit.service.BfAuditLogService;
import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.common.util.RequestUtil;
import com.baiflow.user.dto.response.UserInfo;
import com.baiflow.user.entity.BfUser;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.BfUserMapper;
import com.baiflow.user.service.BfUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现 — 处理登录令牌签发和当前用户信息查询。
 * <p>
 * 集成登录失败限制（Redis 滑动窗口：15 分钟内连续失败 5 次锁定 15 分钟，多实例共享）：
 * 达到阈值时除写入 Redis 锁键外，还将用户状态持久化为 LOCKED；
 * 锁键到期后由 {@link com.baiflow.schedule.LoginLockScheduler} 定时任务（及登录时的兜底判定）恢复为 NORMAL。
 * 同时记录审计日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 最大登录失败次数 */
    private static final int MAX_FAILURES = 5;
    /** 锁定时长（分钟）——同时作为失败计数的滑动窗口 */
    private static final int LOCK_MINUTES = 15;

    private final BfUserMapper userMapper;
    private final BfUserService userService;
    private final SessionTokenService sessionTokenService;
    private final BfUserDeviceService userDeviceService;
    private final BfAuthSessionMapper sessionMapper;
    private final PasswordEncoder passwordEncoder;
    private final BfAuditLogService auditService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public LoginResponse login(LoginRequest request) {
        String ip = RequestUtil.getClientIp();
        String ua = RequestUtil.getClientUserAgent();

        // 0. 检查登录失败锁定（此刻现状为「未锁」：Redis 不可用时按未锁定放行，故障不阻断登录）
        boolean loginLocked = false;
        try {
            loginLocked = Boolean.TRUE.equals(redisTemplate.hasKey(LoginLockRedisKeys.LOCK + request.username()));
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，登录锁定检查按未锁定放行: username={}, error={}", request.username(), e.getMessage());
        }
        if (loginLocked) {
            auditService.log(null, AuditAction.LOGIN_FAILED, AuditTargetType.USER, request.username(), ip, ua, "账号已被临时锁定");
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "登录失败次数过多，账号已临时锁定，请15分钟后再试");
        }

        // 1. 根据用户名查找用户
        BfUser user = userService.getOne(new LambdaQueryWrapper<BfUser>()
                .eq(BfUser::getUsername, request.username())
                .last("LIMIT 1"));
        if (user == null) {
            recordFailure(request.username(), null);
            auditService.log(null, AuditAction.LOGIN_FAILED, AuditTargetType.USER, request.username(), ip, ua, "用户名不存在");
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        // 2. 检查账号状态
        if (user.getStatus() == UserStatus.DISABLED) {
            auditService.log(user.getId(), AuditAction.LOGIN_FAILED, AuditTargetType.USER, user.getId(), ip, ua, "账号已禁用");
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            // 此刻现状为「已锁」：仅确证锁键不存在才解除，Redis 不可用时维持锁定
            boolean lockKeyGone = false;
            try {
                lockKeyGone = Boolean.FALSE.equals(redisTemplate.hasKey(LoginLockRedisKeys.LOCK + user.getUsername()));
            } catch (DataAccessException e) {
                log.warn("Redis 不可用，登录锁定维持不解除: username={}, error={}", user.getUsername(), e.getMessage());
            }
            if (!lockKeyGone) {
                auditService.log(user.getId(), AuditAction.LOGIN_FAILED, AuditTargetType.USER, user.getId(), ip, ua, "账号已锁定");
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "账号已被锁定");
            }
            // 锁键已到期（Redis 确认不存在）：立即恢复状态并继续登录；定时任务 LoginLockScheduler 兜底
            userService.restoreLockedUser(user, ip, ua);
        }

        // 3. 校验密码（BCrypt 比对）
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailure(user.getUsername(), user);
            auditService.log(user.getId(), AuditAction.LOGIN_FAILED, AuditTargetType.USER, user.getId(), ip, ua,
                    "密码错误（剩余尝试次数：" + remainingAttempts(request.username()) + "）");
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        // 4. 登录成功：清除失败计数与锁键，建登录会话（长会话 token）
        userService.clearLoginLock(request.username());
        auditService.log(user.getId(), AuditAction.LOGIN_SUCCESS, AuditTargetType.USER, user.getId(), ip, ua, "登录成功");
        return issueSession(user);
    }

    @Override
    public LoginResponse issueSession(BfUser user) {
        String ua = RequestUtil.getClientUserAgent();

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String deviceType = "ANDROID".equalsIgnoreCase(RequestUtil.getHeader("X-Device-Type")) ? "ANDROID" : "WEB";
        String deviceName = resolveDeviceName(deviceType, RequestUtil.getHeader("X-Device-Name"), ua);
        SessionTokenService.CreatedSession created = sessionTokenService.create(
                user.getId(), deviceType, deviceName, RequestUtil.getClientIp(), ua);
        // 登记登录过的设备（user + device_name 唯一，登出不删，保留历史）
        userDeviceService.recordLogin(user.getId(), deviceName, deviceType);
        return new LoginResponse(created.token(), created.sessionId(), created.expiresAt(), UserInfo.from(user));
    }

    @Override
    public void logout(String token) {
        BfAuthSession session = sessionTokenService.findByToken(token);
        if (session != null) {
            sessionMapper.deleteById(session.getId());
            auditService.log(session.getUserId(), AuditAction.LOGOUT, AuditTargetType.SESSION, session.getId(),
                    RequestUtil.getClientIp(), RequestUtil.getClientUserAgent(), "登出");
            log.info("会话已登出: userId={}, sessionId={}", session.getUserId(), session.getId());
        }
    }

    @Override
    public List<AuthSessionInfo> listSessions(String userId, String currentToken) {
        BfAuthSession current = sessionTokenService.findByToken(currentToken);
        String currentId = current != null ? current.getId() : null;

        LambdaQueryWrapper<BfAuthSession> wrapper = new LambdaQueryWrapper<BfAuthSession>()
                .eq(BfAuthSession::getUserId, userId)
                .orderByDesc(BfAuthSession::getLastUsedAt);
        List<BfAuthSession> sessions = sessionMapper.selectList(wrapper);
        return sessions.stream()
                .map(s -> AuthSessionInfo.from(s, s.getId().equals(currentId)))
                .toList();
    }

    @Override
    public List<UserDeviceInfo> listDevices(String userId, String currentToken) {
        BfAuthSession current = sessionTokenService.findByToken(currentToken);
        String currentDevice = current != null ? current.getDeviceName() : null;

        // 登录设备列表：历史设备全展示（含在线/离线状态）。
        // 在线 = 当前存在未过期会话；强制下线（撤销该设备全部会话）后即变为离线，
        // 离线设备可被「删除」（移除登录历史记录）。
        List<BfUserDevice> devices = userDeviceService.list(new LambdaQueryWrapper<BfUserDevice>()
                .eq(BfUserDevice::getUserId, userId)
                .orderByDesc(BfUserDevice::getLastLoginAt));

        LocalDateTime now = LocalDateTime.now();
        List<BfAuthSession> active = sessionMapper.selectList(new LambdaQueryWrapper<BfAuthSession>()
                .eq(BfAuthSession::getUserId, userId)
                .gt(BfAuthSession::getExpiresAt, now));

        return devices.stream().map(d -> {
            BfAuthSession online = active.stream()
                    .filter(s -> d.getDeviceName().equals(s.getDeviceName()))
                    .max(Comparator.comparing(BfAuthSession::getLastUsedAt))
                    .orElse(null);
            boolean isOnline = online != null;
            return new UserDeviceInfo(d.getDeviceName(), d.getDeviceType(), d.getFirstLoginAt(),
                    d.getLastLoginAt(),
                    isOnline ? online.getLastUsedAt() : d.getLastLoginAt(),
                    isOnline, d.getDeviceName().equals(currentDevice),
                    isOnline ? online.getId() : null);
        }).toList();
    }

    @Override
    public void revokeSession(String userId, boolean isAdmin, String sessionId, String currentToken) {
        BfAuthSession target = sessionMapper.selectById(sessionId);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if (!SecurityUtils.isOwnerOrAdmin(target.getUserId(), userId, isAdmin)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权下线此设备");
        }
        // 强制下线 = 撤销该设备（deviceName）的全部会话（同一设备可能有多次登录的多条活跃会话），
        // 排除当前会话防止误下线自己
        BfAuthSession current = currentToken != null && !currentToken.isBlank()
                ? sessionTokenService.findByToken(currentToken) : null;
        int deleted = deleteSessionsForDevice(target.getUserId(), target.getDeviceName(), current);
        auditService.log(userId, AuditAction.FORCE_LOGOUT, AuditTargetType.SESSION, sessionId,
                RequestUtil.getClientIp(), RequestUtil.getClientUserAgent(),
                "强制下线设备：" + target.getDeviceName() + "（目标用户 " + target.getUserId() + "）共撤销 " + deleted + " 条会话");
        log.info("会话已强制下线: targetUser={}, deviceName={}, sessions={}, by={}", target.getUserId(),
                target.getDeviceName(), deleted, userId);
    }

    @Override
    public void deleteDevice(String userId, String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "设备名不能为空");
        }
        // 仅允许删除离线设备：存在未过期会话 → 需先强制下线（在线设备只能强制下线）
        LocalDateTime now = LocalDateTime.now();
        List<BfAuthSession> active = sessionMapper.selectList(new LambdaQueryWrapper<BfAuthSession>()
                .eq(BfAuthSession::getUserId, userId)
                .eq(BfAuthSession::getDeviceName, deviceName)
                .gt(BfAuthSession::getExpiresAt, now));
        if (!active.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "设备仍在线，请先强制下线再删除");
        }
        // 清理该设备全部（已过期）会话记录 + 删除登录历史记录
        deleteSessionsForDevice(userId, deviceName, null);
        userDeviceService.remove(new LambdaQueryWrapper<BfUserDevice>()
                .eq(BfUserDevice::getUserId, userId)
                .eq(BfUserDevice::getDeviceName, deviceName));
        auditService.log(userId, AuditAction.DELETE_DEVICE, AuditTargetType.DEVICE, deviceName,
                RequestUtil.getClientIp(), RequestUtil.getClientUserAgent(),
                "删除登录设备：" + deviceName);
        log.info("登录设备已删除: user={}, deviceName={}", userId, deviceName);
    }

    /** 删除某用户指定设备名下的全部会话；excludeCurrent 非空时保留该会话（防误下线自己） */
    private int deleteSessionsForDevice(String userId, String deviceName, BfAuthSession excludeCurrent) {
        LambdaQueryWrapper<BfAuthSession> w = new LambdaQueryWrapper<BfAuthSession>()
                .eq(BfAuthSession::getUserId, userId)
                .eq(BfAuthSession::getDeviceName, deviceName);
        if (excludeCurrent != null) {
            w.ne(BfAuthSession::getId, excludeCurrent.getId());
        }
        return sessionMapper.delete(w);
    }

    /**
     * 设备名：优先客户端上报，其次按 UA 推导（Web 场景）。
     */
    private String resolveDeviceName(String deviceType, String deviceName, String userAgent) {
        if (deviceName != null && !deviceName.isBlank()) {
            return deviceName;
        }
        if ("WEB".equals(deviceType)) {
            String ua = userAgent != null ? userAgent : "";
            String browser = "浏览器";
            if (ua.contains("Edg/")) browser = "Edge";
            else if (ua.contains("Firefox/")) browser = "Firefox";
            else if (ua.contains("Chrome/")) browser = "Chrome";
            else if (ua.contains("Safari/")) browser = "Safari";
            String os = "未知系统";
            if (ua.contains("Windows")) os = "Windows";
            else if (ua.contains("Android")) os = "Android";
            else if (ua.contains("iPhone") || ua.contains("iPad")) os = "iOS";
            else if (ua.contains("Mac OS")) os = "macOS";
            else if (ua.contains("Linux")) os = "Linux";
            return browser + " · " + os;
        }
        return "Android 设备";
    }

    @Override
    public void changePassword(String userId, String oldPassword, String newPassword) {
        BfUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "旧密码错误");
        }

        // 更新为新密码
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        // 重置密码后吊销该用户全部登录会话（所有设备强制下线重新登录，含当前设备）
        sessionTokenService.revokeAllExcept(userId, null);
        auditService.log(userId, AuditAction.PASSWORD_CHANGED, AuditTargetType.USER, userId,
                RequestUtil.getClientIp(), RequestUtil.getClientUserAgent(),
                "修改密码，吊销全部登录会话");
        log.info("密码已修改并吊销全部会话: userId={}", userId);
    }

    /**
     * 记录登录失败（滑动窗口）：失败次数 INCR，每次失败刷新窗口 TTL；
     * 窗口内连续失败达阈值则设置锁定键（TTL = LOCK_MINUTES，到期自动解锁，
     * 计数随窗口一并清零，不会"锁到期后一次失误又立即重锁"）。
     * <p>达到阈值时，若用户存在，同时将用户状态持久化为 LOCKED；
     * 锁键到期后由 {@link com.baiflow.schedule.LoginLockScheduler} 定时任务或登录时的兜底判定恢复为 NORMAL。
     */
    private void recordFailure(String username, BfUser user) {
        try {
            String failKey = LoginLockRedisKeys.FAIL_COUNT + username;
            Long count = redisTemplate.opsForValue().increment(failKey);
            // 滑动窗口：从本次失败重新计时
            redisTemplate.expire(failKey, LOCK_MINUTES, TimeUnit.MINUTES);
            if (count != null && count >= MAX_FAILURES) {
                redisTemplate.opsForValue().set(
                        LoginLockRedisKeys.LOCK + username, "1", LOCK_MINUTES, TimeUnit.MINUTES);
                if (user != null) {
                    userMapper.update(null, new LambdaUpdateWrapper<BfUser>()
                            .eq(BfUser::getId, user.getId())
                            .set(BfUser::getStatus, UserStatus.LOCKED));
                    auditService.log(user.getId(), AuditAction.ACCOUNT_LOCKED, AuditTargetType.USER, user.getId(),
                            RequestUtil.getClientIp(), RequestUtil.getClientUserAgent(),
                            "登录失败" + MAX_FAILURES + "次，账号已自动锁定");
                }
            }
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，跳过登录失败计数: {}", e.getMessage());
        }
    }

    /** 获取剩余失败次数 */
    private int remainingAttempts(String username) {
        try {
            String v = redisTemplate.opsForValue().get(LoginLockRedisKeys.FAIL_COUNT + username);
            int count = v == null ? 0 : Integer.parseInt(v);
            return Math.max(0, MAX_FAILURES - count);
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，剩余次数按满额返回: {}", e.getMessage());
            return MAX_FAILURES;
        }
    }
}
