package com.baiflow.user.service.impl;

import com.baiflow.auth.constant.LoginLockRedisKeys;
import com.baiflow.auth.config.BaiflowProperties;
import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.common.util.I18nUtil;
import com.baiflow.file.entity.BfFileItem;
import com.baiflow.file.mapper.BfFileItemMapper;
import com.baiflow.storage.entity.BfStorageRoot;
import com.baiflow.storage.service.BfStorageRootService;
import com.baiflow.user.dto.request.CreateUserRequest;
import com.baiflow.user.dto.request.ResetPasswordRequest;
import com.baiflow.user.dto.request.UpdateUserRequest;
import com.baiflow.user.dto.response.UserInfo;
import com.baiflow.user.entity.BfUser;
import com.baiflow.user.enums.UserRole;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.BfUserMapper;
import com.baiflow.user.service.BfUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 用户管理服务实现。
 */
@Slf4j
@Service
public class BfUserServiceImpl extends ServiceImpl<BfUserMapper, BfUser> implements BfUserService {

    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long AVATAR_MAX_SIZE = 1024 * 1024; // 1MB

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private BfFileItemMapper fileItemMapper;
    @Autowired
    private BfStorageRootService storageService;
    @Autowired
    private I18nUtil i18nUtil;
    @Autowired
    private BaiflowProperties baiflowProperties;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public UserInfo createUser(CreateUserRequest req) {
        // 用户名重复检查
        if (getOne(new LambdaQueryWrapper<BfUser>().eq(BfUser::getUsername, req.username()).last("LIMIT 1")) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS, i18nUtil.translate("用户名已存在：") + req.username());
        }

        BfUser u = new BfUser();
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
        IPage<BfUser> userPage = page(new Page<>(page, size), new LambdaQueryWrapper<BfUser>()
                .eq(role != null && !role.isBlank(), BfUser::getRole, role)
                .eq(status != null && !status.isBlank(), BfUser::getStatus, status)
                .like(displayName != null && !displayName.isBlank(), BfUser::getDisplayName, displayName)
                .orderByDesc(BfUser::getCreatedAt));
        // 转换为 UserInfo 分页结果
        IPage<UserInfo> r = new Page<>(page, size, userPage.getTotal());
        r.setRecords(userPage.getRecords().stream().map(UserInfo::from).toList());
        return r;
    }

    @Override
    public UserInfo getUser(String id) {
        BfUser u = getById(id);
        if (u == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"); }
        return UserInfo.from(u);
    }

    @Override
    public UserInfo updateUser(String id, UpdateUserRequest req) {
        BfUser u = getById(id);
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
        List<BfUser> targets = new ArrayList<>();
        for (String id : ids) {
            BfUser u = getById(id);
            if (u == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, i18nUtil.translate("用户不存在：") + id);
            }
            if (u.getRole() == UserRole.ADMIN) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        i18nUtil.translate("不允许禁用或启用管理员账号"));
            }
            targets.add(u);
        }
        for (BfUser u : targets) {
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
        BfUser u = getById(id);
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
            BfUser u = getById(userId);
            if (u == null) {
                log.warn("批量删除：用户 {} 不存在，跳过", userId);
                continue;
            }

            // 不允许删除系统内置管理员
            if (BUILTIN_ADMIN_USERNAME.equals(u.getUsername())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "不允许删除系统内置管理员账号");
            }

            // 删除该用户拥有的所有文件（磁盘 + 数据库）
            List<BfFileItem> ownedFiles = fileItemMapper.selectList(
                    new LambdaQueryWrapper<BfFileItem>().eq(BfFileItem::getOwnerUserId, userId));
            for (BfFileItem file : ownedFiles) {
                try {
                    BfStorageRoot root = storageService.getByIdOrThrow(file.getStorageRootId());
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

    @Override
    public UserInfo me(String userId) {
        BfUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return UserInfo.from(user);
    }

    @Override
    public UserInfo updateProfile(String userId, String displayName) {
        BfUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        // 展示名不允许为空
        if (displayName == null || displayName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "展示名不能为空");
        }
        user.setDisplayName(displayName.trim());
        updateById(user);
        return UserInfo.from(user);
    }

    @Override
    public UserInfo uploadAvatar(String userId, MultipartFile file) {
        BfUser user = getById(userId);
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
                    i18nUtil.translate("不支持的文件格式，仅允许：") + String.join(", ", ALLOWED_AVATAR_EXTENSIONS));
        }

        // 保存文件到 avatar 目录（文件名带时间戳版本，URL 每次上传唯一，避免浏览器缓存旧头像）
        String avatarDir = baiflowProperties.getStorage().getAvatarPath();
        String oldAvatarUrl = user.getAvatarUrl();
        String avatarFileName = userId + "." + System.currentTimeMillis() + "." + ext;
        Path avatarPath = resolveAvatarWithin(avatarDir, avatarFileName);
        if (avatarPath == null) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "非法的头像路径");
        }

        try {
            Files.createDirectories(avatarPath.getParent());
            file.transferTo(avatarPath.toFile());
        } catch (IOException e) {
            log.error("头像保存失败: userId={}, path={}", userId, avatarPath, e);
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, i18nUtil.translate("头像保存失败：") + e.getMessage());
        }

        // 构建 nginx 访问 URL 并存储
        String avatarUrl = "/avatars/" + avatarFileName;
        user.setAvatarUrl(avatarUrl);
        updateById(user);

        log.info("头像已更新: userId={}, url={}", userId, avatarUrl);

        // 清理旧头像文件（文件名带版本，不覆盖，需删除旧文件避免磁盘残留）
        deleteOldAvatar(oldAvatarUrl, avatarDir, avatarFileName);

        return UserInfo.from(user);
    }

    @Override
    public UserInfo deleteAvatar(String userId) {
        BfUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        String avatarDir = baiflowProperties.getStorage().getAvatarPath();
        Path avatarPath = resolveAvatarPath(user.getAvatarUrl(), avatarDir);
        if (avatarPath != null) {
            try {
                Files.deleteIfExists(avatarPath);
                log.info("头像已删除: path={}", avatarPath);
            } catch (IOException e) {
                log.warn("头像删除失败: path={}", avatarPath, e);
            }
        }
        // avatar_url 列 NOT NULL（DEFAULT ''），"无头像"约定为空字符串
        user.setAvatarUrl("");
        // updateById 默认跳过空值字段，需用 LambdaUpdateWrapper 显式写回空字符串
        update(null, new LambdaUpdateWrapper<BfUser>()
                .eq(BfUser::getId, userId)
                .set(BfUser::getAvatarUrl, ""));
        log.info("头像已清除: userId={}", userId);
        return UserInfo.from(user);
    }

    /** 删除指定 URL 对应的旧头像文件；无旧头像/URL 非法/与新文件同名时跳过。 */
    private void deleteOldAvatar(String oldAvatarUrl, String avatarDir, String newFileName) {
        Path oldPath = resolveAvatarPath(oldAvatarUrl, avatarDir);
        if (oldPath == null || oldPath.getFileName().toString().equals(newFileName)) {
            return;
        }
        try {
            Files.deleteIfExists(oldPath);
            log.info("旧头像已删除: path={}", oldPath);
        } catch (IOException e) {
            log.warn("旧头像删除失败: path={}", oldPath, e);
        }
    }

    /** 解析头像 URL 对应的文件路径（归一化 + 目录穿越校验）；非 /avatars/ 前缀或非法返回 null */
    private Path resolveAvatarPath(String avatarUrl, String avatarDir) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        String prefix = "/avatars/";
        if (!avatarUrl.startsWith(prefix)) {
            return null;
        }
        String fileName = avatarUrl.substring(prefix.length());
        int queryIdx = fileName.indexOf('?');
        if (queryIdx >= 0) {
            fileName = fileName.substring(0, queryIdx);
        }
        if (fileName.isEmpty()) {
            return null;
        }
        return resolveAvatarWithin(avatarDir, fileName);
    }

    /** 归一化并校验文件名位于 avatar 目录内，返回安全路径；穿越则返回 null（uploadAvatar 与 deleteAvatar 共用） */
    private Path resolveAvatarWithin(String avatarDir, String fileName) {
        Path dir = Path.of(avatarDir).normalize();
        Path path = Path.of(avatarDir, fileName).normalize();
        return path.startsWith(dir) ? path : null;
    }
}
