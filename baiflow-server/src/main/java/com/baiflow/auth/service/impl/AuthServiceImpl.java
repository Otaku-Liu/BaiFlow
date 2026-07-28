package com.baiflow.auth.service.impl;

import com.baiflow.auth.config.BaiflowProperties;
import com.baiflow.auth.dto.request.LoginRequest;
import com.baiflow.auth.dto.response.LoginResponse;
import com.baiflow.auth.security.JwtService;
import com.baiflow.auth.service.AccountLockService;
import com.baiflow.auth.service.AuthService;
import com.baiflow.audit.service.AuditService;
import com.baiflow.common.entity.ApiResponse.Code;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.user.dto.response.UserInfo;
import com.baiflow.user.entity.User;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 认证服务实现 — 处理登录令牌签发和当前用户信息查询。
 * <p>
 * 集成登录失败限制（5 次失败锁定 15 分钟）和审计日志记录。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long AVATAR_MAX_SIZE = 1024 * 1024; // 1MB

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AccountLockService accountLockService;
    private final AuditService auditService;
    private final BaiflowProperties baiflowProperties;

    public AuthServiceImpl(UserMapper userMapper, JwtService jwtService,
                           PasswordEncoder passwordEncoder,
                           AccountLockService accountLockService,
                           AuditService auditService,
                           BaiflowProperties baiflowProperties) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.accountLockService = accountLockService;
        this.auditService = auditService;
        this.baiflowProperties = baiflowProperties;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String ip = getClientIp();
        String ua = getClientUserAgent();

        // 0. 检查登录失败锁定
        if (accountLockService.isLocked(request.username())) {
            auditService.log(null, "LOGIN_FAILED", "USER", request.username(), ip, ua, "账号已被临时锁定");
            throw new BusinessException(Code.ACCOUNT_LOCKED, "登录失败次数过多，账号已临时锁定，请15分钟后再试");
        }

        // 1. 根据用户名查找用户
        User user = userMapper.selectByUsername(request.username());
        if (user == null) {
            accountLockService.recordFailure(request.username());
            auditService.log(null, "LOGIN_FAILED", "USER", request.username(), ip, ua, "用户名不存在");
            throw new BusinessException(Code.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        // 2. 检查账号状态
        if (user.getStatus() == UserStatus.DISABLED) {
            auditService.log(user.getId(), "LOGIN_FAILED", "USER", user.getId(), ip, ua, "账号已禁用");
            throw new BusinessException(Code.ACCOUNT_DISABLED, "账号已被禁用");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            auditService.log(user.getId(), "LOGIN_FAILED", "USER", user.getId(), ip, ua, "账号已锁定");
            throw new BusinessException(Code.ACCOUNT_LOCKED, "账号已被锁定");
        }

        // 3. 校验密码（BCrypt 比对）
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            accountLockService.recordFailure(request.username());
            auditService.log(user.getId(), "LOGIN_FAILED", "USER", user.getId(), ip, ua,
                    "密码错误（剩余尝试次数：" + accountLockService.remainingAttempts(request.username()) + "）");
            throw new BusinessException(Code.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        // 4. 登录成功：清除失败计数，更新最后登录时间，签发 JWT
        accountLockService.clearFailures(request.username());
        auditService.log(user.getId(), "LOGIN_SUCCESS", "USER", user.getId(), ip, ua, "登录成功");

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return new LoginResponse(token, UserInfo.from(user));
    }

    @Override
    public UserInfo me(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(Code.NOT_FOUND, "用户不存在");
        }
        return UserInfo.from(user);
    }

    private String getClientIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                var req = attrs.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private String getClientUserAgent() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String ua = attrs.getRequest().getHeader("User-Agent");
                return ua != null ? ua : "";
            }
        } catch (Exception ignored) {}
        return "";
    }

    @Override
    public UserInfo updateProfile(String userId, String displayName) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(Code.NOT_FOUND, "用户不存在");
        }
        user.setDisplayName(displayName != null ? displayName : "");
        userMapper.updateById(user);
        return UserInfo.from(user);
    }

    @Override
    public UserInfo uploadAvatar(String userId, MultipartFile file) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(Code.NOT_FOUND, "用户不存在");
        }

        // 校验文件大小
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "头像文件大小不能超过 1MB");
        }

        // 校验文件格式
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "文件名不能为空");
        }
        String ext = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = originalName.substring(dotIdx + 1).toLowerCase();
        }
        if (!ALLOWED_AVATAR_EXTENSIONS.contains(ext)) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED,
                    "不支持的文件格式，仅允许: " + String.join(", ", ALLOWED_AVATAR_EXTENSIONS));
        }

        // 保存文件到 avatar 目录
        String avatarDir = baiflowProperties.getStorage().getAvatarPath();
        String avatarFileName = userId + "." + ext;
        Path avatarPath = Path.of(avatarDir, avatarFileName).normalize();

        // 防止路径穿越
        if (!avatarPath.startsWith(Path.of(avatarDir).normalize())) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "非法的头像路径");
        }

        try {
            Files.createDirectories(avatarPath.getParent());
            file.transferTo(avatarPath.toFile());
        } catch (IOException e) {
            log.error("头像保存失败: userId={}, path={}", userId, avatarPath, e);
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "头像保存失败: " + e.getMessage());
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
            throw new BusinessException(Code.NOT_FOUND, "用户不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(Code.INVALID_CREDENTIALS, "旧密码错误");
        }

        // 更新为新密码
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("密码已修改: userId={}", userId);
    }
}
