package com.baiflow.user.service.impl;

import com.baiflow.auth.constant.LoginLockRedisKeys;
import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.common.util.I18nUtil;
import com.baiflow.file.entity.FileItem;
import com.baiflow.file.mapper.FileItemMapper;
import com.baiflow.storage.entity.StorageRoot;
import com.baiflow.storage.service.StorageService;
import com.baiflow.user.dto.request.CreateUserRequest;
import com.baiflow.user.dto.request.ResetPasswordRequest;
import com.baiflow.user.dto.request.UpdateUserRequest;
import com.baiflow.user.dto.response.UserInfo;
import com.baiflow.user.entity.User;
import com.baiflow.user.enums.UserRole;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.UserMapper;
import com.baiflow.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户管理服务实现。
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private FileItemMapper fileItemMapper;
    @Autowired
    private StorageService storageService;
    @Autowired
    private I18nUtil i18nUtil;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public UserInfo createUser(CreateUserRequest req) {
        // 用户名重复检查
        if (getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.username()).last("LIMIT 1")) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS, i18nUtil.translate("用户名已存在：") + req.username());
        }

        User u = new User();
        u.setUsername(req.username());
        // 密码 BCrypt 哈希后再存储——绝不存明文
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setDisplayName(req.displayName() != null ? req.displayName() : "");
        u.setRole(req.role());
        u.setStatus(UserStatus.NORMAL);
        save(u);
        return UserInfo.from(u);
    }

    @Override
    public IPage<UserInfo> listUsers(int page, int size, String role, String status, String displayName) {
        // 使用 MyBatis-Plus 分页插件进行数据库级分页
        IPage<User> userPage = page(new Page<>(page, size), new LambdaQueryWrapper<User>()
                .eq(role != null && !role.isBlank(), User::getRole, role)
                .eq(status != null && !status.isBlank(), User::getStatus, status)
                .like(displayName != null && !displayName.isBlank(), User::getDisplayName, displayName)
                .orderByDesc(User::getCreatedAt));
        // 转换为 UserInfo 分页结果
        IPage<UserInfo> r = new Page<>(page, size, userPage.getTotal());
        r.setRecords(userPage.getRecords().stream().map(UserInfo::from).toList());
        return r;
    }

    @Override
    public UserInfo getUser(String id) {
        User u = getById(id);
        if (u == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"); }
        return UserInfo.from(u);
    }

    @Override
    public UserInfo updateUser(String id, UpdateUserRequest req) {
        User u = getById(id);
        if (u == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"); }
        // 管理员不支持手动锁定：LOCKED 状态仅由登录失败自动锁定维护，锁键到期后自动恢复
        if (req.status() == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    i18nUtil.translate("不允许手动锁定用户，仅支持禁用或恢复"));
        }
        UserStatus oldStatus = u.getStatus();
        // 仅更新实际传入的字段
        if (req.displayName() != null) { u.setDisplayName(req.displayName()); }
        if (req.role() != null) { u.setRole(req.role()); }
        if (req.status() != null) { u.setStatus(req.status()); }
        updateById(u);
        // 从锁定状态改为其他状态（如禁用）时，清除 Redis 锁键与失败计数，避免残留锁定
        if (oldStatus == UserStatus.LOCKED && u.getStatus() != UserStatus.LOCKED) {
            clearLoginLock(u.getUsername());
        }
        return UserInfo.from(u);
    }

    @Override
    @Transactional
    public void batchUpdateStatus(List<String> ids, UserStatus targetStatus) {
        // 不支持手动锁定
        if (targetStatus == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    i18nUtil.translate("不允许手动锁定用户，仅支持禁用或恢复"));
        }
        // 先整体校验：目标必须存在且仅限 USER 角色，避免中途失败产生部分更新
        List<User> targets = new ArrayList<>();
        for (String id : ids) {
            User u = getById(id);
            if (u == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, i18nUtil.translate("用户不存在：") + id);
            }
            if (u.getRole() == UserRole.ADMIN) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        i18nUtil.translate("不允许禁用或启用管理员账号"));
            }
            targets.add(u);
        }
        for (User u : targets) {
            UserStatus oldStatus = u.getStatus();
            u.setStatus(targetStatus);
            updateById(u);
            if (oldStatus == UserStatus.LOCKED && targetStatus != UserStatus.LOCKED) {
                clearLoginLock(u.getUsername());
            }
        }
        log.info("批量设置用户状态完成: count={}, status={}", targets.size(), targetStatus);
    }

    /**
     * 清除用户的登录锁定 Redis 键（锁键 + 失败计数）。
     * <p>将锁定中的用户改为其他状态时调用，避免残留锁键在下次登录时仍拦截。
     * Redis 不可用时降级跳过（锁键本身会随 TTL 到期），不影响状态变更。
     */
    private void clearLoginLock(String username) {
        try {
            redisTemplate.delete(LoginLockRedisKeys.LOCK + username);
            redisTemplate.delete(LoginLockRedisKeys.FAIL_COUNT + username);
            log.info("已清除用户登录锁定: username={}", username);
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，跳过清除登录锁定: username={}, error={}", username, e.getMessage());
        }
    }

    @Override
    public void resetPassword(String id, ResetPasswordRequest req) {
        User u = getById(id);
        if (u == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"); }
        // 新密码重新 BCrypt 哈希，完全覆盖旧密码
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        updateById(u);
    }

    /** 系统内置管理员用户名，禁止被删除 */
    private static final String BUILTIN_ADMIN_USERNAME = "admin";

    @Override
    @Transactional
    public void batchDelete(List<String> ids, String currentUserId) {
        // 不允许删除自己
        if (ids.contains(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不允许删除当前登录用户");
        }

        for (String userId : ids) {
            User u = getById(userId);
            if (u == null) {
                log.warn("批量删除：用户 {} 不存在，跳过", userId);
                continue;
            }

            // 不允许删除系统内置管理员
            if (BUILTIN_ADMIN_USERNAME.equals(u.getUsername())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "不允许删除系统内置管理员账号");
            }

            // 删除该用户拥有的所有文件（磁盘 + 数据库）
            List<FileItem> ownedFiles = fileItemMapper.selectList(
                    new LambdaQueryWrapper<FileItem>().eq(FileItem::getOwnerUserId, userId));
            for (FileItem file : ownedFiles) {
                try {
                    StorageRoot root = storageService.getByIdOrThrow(file.getStorageRootId());
                    Path filePath = storageService.resolveRootPath(root)
                            .resolve(file.getRelativePath()).normalize();
                    storageService.verifyPathInRoot(root, filePath);
                    Files.deleteIfExists(filePath);
                } catch (Exception e) {
                    log.warn("删除用户文件失败: userId={}, fileId={}, path={}, error={}",
                            userId, file.getId(), file.getRelativePath(), e.getMessage());
                }
                fileItemMapper.deleteById(file.getId());
            }
            log.info("已删除用户 {} ({}) 的 {} 个文件", u.getUsername(), userId, ownedFiles.size());

            // 删除用户记录（下载/分享记录因 denormalized owner 字段保留）
            removeById(userId);
        }
    }
}
