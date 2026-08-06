package com.baiflow.auth.service.impl;

import com.baiflow.auth.config.BaiflowProperties;
import com.baiflow.auth.dto.request.LoginRequest;
import com.baiflow.auth.dto.response.AuthSessionInfo;
import com.baiflow.auth.dto.response.LoginResponse;
import com.baiflow.auth.entity.AuthSession;
import com.baiflow.auth.mapper.AuthSessionMapper;
import com.baiflow.auth.security.SessionTokenService;
import com.baiflow.auth.service.AccountLockService;
import com.baiflow.auth.service.AuthService;
import com.baiflow.audit.service.AuditService;
import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.common.util.RequestUtil;
import com.baiflow.user.dto.response.UserInfo;
import com.baiflow.user.entity.User;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 认证服务实现 — 处理登录令牌签发和当前用户信息查询。
 * <p>
 * 集成登录失败限制（5 次失败锁定 15 分钟）和审计日志记录。
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long AVATAR_MAX_SIZE = 1024 * 1024; // 1MB

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private SessionTokenService sessionTokenService;
    @Autowired
    private AuthSessionMapper sessionMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AccountLockService accountLockService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private BaiflowProperties baiflowProperties;

    @Override
    public LoginResponse login(LoginRequest request) {
        String ip = RequestUtil.getClientIp();
        String ua = RequestUtil.getClientUserAgent();

        // 0. 检查登录失败锁定
        if (accountLockService.isLocked(request.username())) {
            auditService.log(null, "LOGIN_FAILED", "USER", request.username(), ip, ua, "账号已被临时锁定");
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "登录失败次数过多，账号已临时锁定，请15分钟后再试");
        }

        // 1. 根据用户名查找用户
        User user = userMapper.selectByUsername(request.username());
        if (user == null) {
            accountLockService.recordFailure(request.username());
            auditService.log(null, "LOGIN_FAILED", "USER", request.username(), ip, ua, "用户名不存在");
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        // 2. 检查账号状态
        if (user.getStatus() == UserStatus.DISABLED) {
            auditService.log(user.getId(), "LOGIN_FAILED", "USER", user.getId(), ip, ua, "账号已禁用");
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            auditService.log(user.getId(), "LOGIN_FAILED", "USER", user.getId(), ip, ua, "账号已锁定");
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "账号已被锁定");
        }

        // 3. 校验密码（BCrypt 比对）
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            accountLockService.recordFailure(request.username());
            auditService.log(user.getId(), "LOGIN_FAILED", "USER", user.getId(), ip, ua,
                    "密码错误（剩余尝试次数：" + accountLockService.remainingAttempts(request.username()) + "）");
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        // 4. 登录成功：清除失败计数，更新最后登录时间，建登录会话（长会话 token）
        accountLockService.clearFailures(request.username());
        auditService.log(user.getId(), "LOGIN_SUCCESS", "USER", user.getId(), ip, ua, "登录成功");

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String deviceType = "ANDROID".equalsIgnoreCase(RequestUtil.getHeader("X-Device-Type")) ? "ANDROID" : "WEB";
        String deviceName = resolveDeviceName(deviceType, RequestUtil.getHeader("X-Device-Name"), ua);
        SessionTokenService.CreatedSession created = sessionTokenService.create(
                user.getId(), deviceType, deviceName, ip, ua);
        return new LoginResponse(created.token(), created.sessionId(), created.expiresAt(), UserInfo.from(user));
    }

    @Override
    public UserInfo me(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return UserInfo.from(user);
    }

    @Override
    public void logout(String token) {
        AuthSession session = sessionTokenService.findByToken(token);
        if (session != null) {
            sessionTokenService.revoke(session.getId());
            log.info("会话已登出: userId={}, sessionId={}", session.getUserId(), session.getId());
        }
    }

    @Override
    public List<AuthSessionInfo> listSessions(String userId, String currentToken) {
        AuthSession current = sessionTokenService.findByToken(currentToken);
        String currentId = current != null ? current.getId() : null;

        QueryWrapper<AuthSession> wrapper = new QueryWrapper<AuthSession>()
                .eq("user_id", userId)
                .isNull("revoked_at")
                .orderByDesc("last_used_at");
        List<AuthSession> sessions = sessionMapper.selectList(wrapper);
        return sessions.stream()
                .map(s -> AuthSessionInfo.from(s, s.getId().equals(currentId)))
                .toList();
    }

    @Override
    public void revokeSession(String userId, boolean isAdmin, String sessionId) {
        AuthSession target = sessionMapper.selectById(sessionId);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if (!isAdmin && !userId.equals(target.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权下线此设备");
        }
        sessionTokenService.revoke(sessionId);
        log.info("会话已强制下线: targetUser={}, sessionId={}, by={}", target.getUserId(), sessionId, userId);
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
    public UserInfo updateProfile(String userId, String displayName) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setDisplayName(displayName != null ? displayName : "");
        userMapper.updateById(user);
        return UserInfo.from(user);
    }

    @Override
    public UserInfo uploadAvatar(String userId, MultipartFile file) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 校验文件大小
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "头像文件大小不能超过 1MB");
        }

        // 校验文件格式
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "文件名不能为空");
        }
        String ext = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = originalName.substring(dotIdx + 1).toLowerCase();
        }
        if (!ALLOWED_AVATAR_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED,
                    "不支持的文件格式，仅允许: " + String.join(", ", ALLOWED_AVATAR_EXTENSIONS));
        }

        // 保存文件到 avatar 目录
        String avatarDir = baiflowProperties.getStorage().getAvatarPath();
        String avatarFileName = userId + "." + ext;
        Path avatarPath = Path.of(avatarDir, avatarFileName).normalize();

        // 防止路径穿越
        if (!avatarPath.startsWith(Path.of(avatarDir).normalize())) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "非法的头像路径");
        }

        try {
            Files.createDirectories(avatarPath.getParent());
            file.transferTo(avatarPath.toFile());
        } catch (IOException e) {
            log.error("头像保存失败: userId={}, path={}", userId, avatarPath, e);
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "头像保存失败: " + e.getMessage());
        }

        // 构建 nginx 访问 URL 并存储
        String avatarUrl = "/avatars/" + avatarFileName;
        user.setAvatarUrl(avatarUrl);
        userMapper.updateById(user);

        log.info("头像已更新: userId={}, url={}", userId, avatarUrl);
        return UserInfo.from(user);
    }

    @Override
    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
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
        sessionTokenService.revokeAll(userId);
        log.info("密码已修改并吊销全部会话: userId={}", userId);
    }
}
