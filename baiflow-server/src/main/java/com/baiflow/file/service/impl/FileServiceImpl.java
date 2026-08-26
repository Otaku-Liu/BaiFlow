package com.baiflow.file.service.impl;

import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.common.util.I18nUtil;
import com.baiflow.downloadrecord.dto.response.DownloadRecordInfo;
import com.baiflow.downloadrecord.service.DownloadRecordService;
import com.baiflow.file.dto.request.CreateFolderRequest;
import com.baiflow.file.dto.request.MoveRequest;
import com.baiflow.file.dto.request.RenameRequest;
import com.baiflow.file.dto.request.SetPrivacyRequest;
import com.baiflow.file.dto.request.VerifyPrivacyRequest;
import com.baiflow.file.dto.response.FileItemInfo;
import com.baiflow.file.entity.FileItem;
import com.baiflow.file.entity.PlaybackProgress;
import com.baiflow.file.entity.PrivateFolderAccess;
import com.baiflow.file.enums.FileItemStatus;
import com.baiflow.file.enums.ItemType;
import com.baiflow.file.enums.PrivacyMode;
import com.baiflow.file.mapper.FileItemMapper;
import com.baiflow.file.service.FileConvertService;
import com.baiflow.file.service.FileService;
import com.baiflow.file.service.PlaybackProgressService;
import com.baiflow.file.service.PrivateFolderAccessService;
import com.baiflow.storage.entity.StorageRoot;
import com.baiflow.storage.entity.UserStoragePermission;
import com.baiflow.storage.enums.StorageRootStatus;
import com.baiflow.storage.service.StorageService;
import com.baiflow.storage.service.UserStoragePermissionService;
import com.baiflow.user.entity.User;
import com.baiflow.user.enums.UserRole;
import com.baiflow.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 文件服务实现 — 所有文件操作均强制遵循存储根目录边界约束和用户权限校验。
 * <p>
 * 非管理员用户仅能访问和操作自己的文件（按 ownerUserId 隔离），
 * 管理员可查看所有文件并可为操作指定目标用户视角。
 */
@Slf4j
@Service
public class FileServiceImpl extends ServiceImpl<FileItemMapper, FileItem> implements FileService {

    private static final int ACCESS_TOKEN_BYTES = 32;
    /** 隐私文件夹访问会话有效期（分钟） */
    private static final int ACCESS_SESSION_MINUTES = 30;

    @Autowired
    private I18nUtil i18nUtil;
    @Autowired
    private StorageService storageService;
    @Autowired
    private UserStoragePermissionService userStoragePermissionService;
    @Autowired
    private PrivateFolderAccessService privateFolderAccessService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PlaybackProgressService playbackProgressService;
    @Autowired
    private DownloadRecordService downloadRecordService;
    @Autowired
    private FileConvertService convertService;

    @Override
    public IPage<FileItemInfo> listFiles(String rootId, String parentId, int page, int size,
                                         String userId, boolean isAdmin, String privacyAccessToken,
                                         String viewUserId, String sort, String dir) {
        storageService.getByIdOrThrow(rootId);

        // 归一化排序字段与方向：非法字段回落 name；dir 缺省按惯例（名称升序 / 创建时间降序 / 大小降序）
        String effectiveSort = "createdAt".equals(sort) ? "createdAt" : "size".equals(sort) ? "size" : "name";
        String effectiveDir = (dir == null || dir.isBlank())
                ? ("name".equals(effectiveSort) ? "asc" : "desc")
                : dir;

        // 非管理员：只能看自己的文件，且以主目录为根
        String effectiveOwner = isAdmin && viewUserId != null ? viewUserId : userId;
        boolean openedFolder = parentId != null && !parentId.isBlank();
        if (!isAdmin || viewUserId != null) {
            // 确保用户存在
            User u = userMapper.selectById(effectiveOwner);
            if (u == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
            }
            // 自动创建该用户的主目录，并将用户的文件视图限定在主目录内
            String homeId = getOrCreateHomeFolder(rootId, effectiveOwner, u.getUsername());
            if (parentId == null || parentId.isBlank()) {
                parentId = homeId;
            }
        }

        // 进入文件夹前检查隐私保护
        checkPrivacyAccess(parentId, userId, privacyAccessToken);

        // 进入具体文件夹时记录该目录的上次打开时间（root 视图 parentId 为空不记录）
        if (openedFolder) {
            touchFolderOpen(parentId, userId, isAdmin);
        }

        List<FileItem> items;
        if (!isAdmin || viewUserId != null) {
            // 限定到指定用户的文件
            items = list(childrenWrapper(rootId, parentId, effectiveOwner, FileItemStatus.ACTIVE.name(),
                    effectiveSort, effectiveDir));
        } else {
            // 管理员查看所有文件
            items = list(childrenWrapper(rootId, parentId, null, FileItemStatus.ACTIVE.name(),
                    effectiveSort, effectiveDir));
        }

        int total = items.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<FileItem> subList = from < total ? items.subList(from, to) : List.of();
        // 批量统计本页文件的下载次数（CLIENT + SHARE 均计入）
        Map<String, Long> counts = downloadRecordService.countByFileIds(
                subList.stream().map(FileItem::getId).toList());
        // 批量统计本页目录的直接子项数（文件 + 子文件夹）；隐私目录不返回（null）
        List<String> dirIds = subList.stream()
                .filter(f -> f.getItemType() == ItemType.DIRECTORY)
                .map(FileItem::getId)
                .toList();
        Map<String, Long> childCounts = dirIds.isEmpty() ? java.util.Map.of() : childCountsByParents(dirIds);
        List<FileItemInfo> recs = subList.stream()
                .map(f -> FileItemInfo.from(f,
                        counts.getOrDefault(f.getId(), 0L).intValue(),
                        f.getItemType() == ItemType.DIRECTORY && f.getPrivacyMode() != PrivacyMode.PRIVATE
                                ? childCounts.get(f.getId()) : null))
                .toList();
        IPage<FileItemInfo> r = new Page<>(page, size, total);
        r.setRecords(recs);
        return r;
    }

    @Override
    @Transactional
    public FileItemInfo uploadFile(String rootId, String parentId, MultipartFile file,
                                   String userId, String effectiveUserId, String privacyAccessToken) {
        StorageRoot root = storageService.getByIdOrThrow(rootId);
        // NAS 离线时禁止写入
        requireStorageAvailable(root);

        // 所有用户（含 ADMIN 切换空间时）限定在 effectiveUserId 的主目录内操作
        parentId = scopeToHome(rootId, effectiveUserId, parentId);

        // 上传到隐私文件夹内需先验证隐私密码
        checkPrivacyAccess(parentId, userId, privacyAccessToken);

        // 清洗文件名，构建相对路径
        String safe = sanitize(file.getOriginalFilename());
        String rel = buildPath(parentId, safe);

        // 检查同名文件
        if (findByPath(rootId, rel) != null) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, i18nUtil.translate("文件已存在：") + safe);
        }

        // 解析目标路径并执行路径穿越校验
        Path rootPath = storageService.resolveRootPath(root);
        Path target = rootPath.resolve(rel).normalize();
        storageService.verifyPathInRoot(root, target);

        // 确保父目录存在
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, i18nUtil.translate("无法创建父目录：") + e.getMessage());
        }

        // 写入文件并计算 SHA-256 哈希
        String sha;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            sha = hash(target);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, i18nUtil.translate("文件写入失败：") + e.getMessage());
        }

        // 持久化元数据（磁盘写入成功后才写库）
        FileItem f = new FileItem();
        f.setStorageRootId(rootId);
        f.setParentId(blankNull(parentId));
        f.setOwnerUserId(effectiveUserId);
        f.setName(safe);
        f.setRelativePath(rel);
        f.setItemType(ItemType.FILE);
        f.setSizeBytes(file.getSize());
        f.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        f.setHashSha256(sha);
        f.setPrivacyMode(PrivacyMode.NORMAL);
        f.setStatus(FileItemStatus.ACTIVE);
        save(f);

        return FileItemInfo.from(f);
    }

    @Override
    public Resource downloadFile(String fileId, String userId, boolean isAdmin, String privacyAccessToken) {
        // 检查元数据是否存在且为 FILE（不支持直接下载目录）
        FileItem f = getById(fileId);
        if (f == null || f.getStatus() != FileItemStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        if (f.getItemType() != ItemType.FILE) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "无法下载文件夹");
        }

        checkOwnership(f, userId, isAdmin);

        // 下载隐私文件夹内的文件需验证隐私密码
        checkPrivacyAccess(f.getParentId(), userId, privacyAccessToken);

        // 解析磁盘路径，校验边界
        StorageRoot root = storageService.getByIdOrThrow(f.getStorageRootId());
        Path fp = storageService.resolveRootPath(root).resolve(f.getRelativePath()).normalize();
        storageService.verifyPathInRoot(root, fp);

        if (!Files.exists(fp)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "磁盘文件不存在");
        }
        // 记录上次打开时间（预览复用本方法，故预览/下载都会更新）
        touchLastOpened(f.getId());
        return new FileSystemResource(fp);
    }

    @Override
    public Long computeSize(String id, String userId, boolean isAdmin, String privacyAccessToken) {
        FileItem f = getById(id);
        if (f == null || f.getStatus() != FileItemStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        checkOwnership(f, userId, isAdmin);
        if (f.getItemType() != ItemType.DIRECTORY) {
            // 文件：隐私来自父目录链
            checkPrivacyAccess(f.getParentId(), userId, privacyAccessToken);
            return f.getSizeBytes() != null ? f.getSizeBytes() : 0L;
        }
        // 目录：从自身开始查隐私——隐私文件夹本身未解锁不可计算大小（与 glossary「隐私文件夹不提供大小」一致）
        checkPrivacyAccess(f.getId(), userId, privacyAccessToken);
        Long sum = baseMapper.sumFolderSize(id);
        return sum != null ? sum : 0L;
    }

    @Override
    public IPage<DownloadRecordInfo> listFileDownloads(String fileId, String userId, boolean isAdmin,
                                                       int page, int size) {
        FileItem f = getById(fileId);
        if (f == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在"); }
        checkOwnership(f, userId, isAdmin);
        return downloadRecordService.pageByFileId(fileId, page, size);
    }

    @Override
    @Transactional
    public FileItemInfo createFolder(CreateFolderRequest req, String userId, String effectiveUserId,
                                     String privacyAccessToken) {
        StorageRoot root = storageService.getByIdOrThrow(req.storageRootId());
        // NAS 离线时禁止写入
        requireStorageAvailable(root);

        // 所有用户（含 ADMIN 切换空间时）限定在 effectiveUserId 的主目录内操作
        String effectiveParentId = scopeToHome(req.storageRootId(), effectiveUserId, req.parentId());

        // 在隐私文件夹内创建子文件夹需先验证隐私密码
        checkPrivacyAccess(effectiveParentId, userId, privacyAccessToken);

        String safe = sanitize(req.name());
        String rel = buildPath(effectiveParentId, safe);

        if (findByPath(req.storageRootId(), rel) != null) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, i18nUtil.translate("文件夹已存在：") + safe);
        }

        // 解析并校验路径，在磁盘上创建目录
        Path target = storageService.resolveRootPath(root).resolve(rel).normalize();
        storageService.verifyPathInRoot(root, target);
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, i18nUtil.translate("无法创建文件夹：") + e.getMessage());
        }

        // 持久化元数据
        FileItem f = new FileItem();
        f.setStorageRootId(req.storageRootId());
        f.setParentId(blankNull(effectiveParentId));
        f.setOwnerUserId(effectiveUserId);
        f.setName(safe);
        f.setRelativePath(rel);
        f.setItemType(ItemType.DIRECTORY);
        f.setSizeBytes(0L);
        f.setPrivacyMode(PrivacyMode.NORMAL);
        f.setStatus(FileItemStatus.ACTIVE);
        save(f);

        return FileItemInfo.from(f);
    }

    @Override
    @Transactional
    public FileItemInfo rename(String id, RenameRequest req, String userId, boolean isAdmin,
                               String privacyAccessToken) {
        FileItem f = checkActive(id);
        checkOwnership(f, userId, isAdmin);
        assertMutable(f, "重命名");
        assertNotPrivate(f, "重命名");
        // NAS 离线时禁止写入
        requireStorageAvailable(storageService.getByIdOrThrow(f.getStorageRootId()));

        // 重命名隐私文件夹内的项目需验证隐私密码
        checkPrivacyAccess(f.getParentId(), userId, privacyAccessToken);

        String nn = sanitize(req.newName());
        String nr = newRelPath(f.getRelativePath(), nn);

        // 磁盘重命名
        Path old = storageService.resolveRootPath(storageService.getByIdOrThrow(f.getStorageRootId()))
                .resolve(f.getRelativePath()).normalize();
        Path np = old.getParent().resolve(nn).normalize();
        try {
            Files.move(old, np);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, i18nUtil.translate("重命名失败：") + e.getMessage());
        }

        // 更新元数据
        f.setName(nn);
        f.setRelativePath(nr);
        updateById(f);
        return FileItemInfo.from(f);
    }

    @Override
    @Transactional
    public FileItemInfo move(String id, MoveRequest req, String userId, boolean isAdmin,
                             String privacyAccessToken) {
        FileItem f = checkActive(id);
        checkOwnership(f, userId, isAdmin);
        // NAS 离线时禁止写入
        requireStorageAvailable(storageService.getByIdOrThrow(f.getStorageRootId()));

        // 移动隐私文件夹内的项目需验证隐私密码
        checkPrivacyAccess(f.getParentId(), userId, privacyAccessToken);

        StorageRoot tr = storageService.getByIdOrThrow(req.targetStorageRootId());
        String nr = buildPath(req.targetParentId(), f.getName());

        // 磁盘移动（跨根目录支持）
        Path old = storageService.resolveRootPath(storageService.getByIdOrThrow(f.getStorageRootId()))
                .resolve(f.getRelativePath()).normalize();
        Path np = storageService.resolveRootPath(tr).resolve(nr).normalize();
        storageService.verifyPathInRoot(tr, np);

        try {
            Files.createDirectories(np.getParent());
            Files.move(old, np);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, i18nUtil.translate("移动失败：") + e.getMessage());
        }

        // 更新元数据中的存储根和父节点
        f.setStorageRootId(req.targetStorageRootId());
        f.setParentId(blankNull(req.targetParentId()));
        f.setRelativePath(nr);
        updateById(f);
        return FileItemInfo.from(f);
    }

    @Override
    @Transactional
    public void delete(String id, String userId, boolean isAdmin, String privacyAccessToken) {
        FileItem f = checkActive(id);
        checkOwnership(f, userId, isAdmin);
        assertMutable(f, "删除");
        assertNotPrivate(f, "删除");
        // NAS 离线时禁止写入
        requireStorageAvailable(storageService.getByIdOrThrow(f.getStorageRootId()));

        // 删除隐私文件夹内的项目需验证隐私密码
        checkPrivacyAccess(f.getParentId(), userId, privacyAccessToken);

        // 先标记元数据为已删除（软删除），再删除磁盘文件
        // 如果磁盘删除失败，元数据已安全标记，后续可由清理任务修复
        f.setStatus(FileItemStatus.DELETED);
        f.setDeletedAt(LocalDateTime.now());
        updateById(f);

        // 级联删除该文件的播放/阅读进度，避免残留孤儿进度
        playbackProgressService.remove(new LambdaQueryWrapper<PlaybackProgress>()
                .eq(PlaybackProgress::getFileItemId, id));

        StorageRoot root = storageService.getByIdOrThrow(f.getStorageRootId());
        Path p = storageService.resolveRootPath(root).resolve(f.getRelativePath()).normalize();
        storageService.verifyPathInRoot(root, p);

        try {
            if (Files.isDirectory(p)) {
                // 递归删除目录树
                try (Stream<Path> s = Files.walk(p)) {
                    s.sorted(Comparator.reverseOrder())
                            .forEach(x -> {
                                try {
                                    Files.delete(x);
                                } catch (IOException ignored) {
                                }
                            });
                }
            } else {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
            // 磁盘删除失败不抛异常——元数据已标记 DELETED，后续可修复
        }
    }

    // -------------------------------------------------------
    // 隐私文件夹方法
    // -------------------------------------------------------

    @Override
    @Transactional
    public FileItemInfo setPrivacy(String id, SetPrivacyRequest req, String userId) {
        FileItem f = checkActive(id);
        assertMutable(f, "设置隐私");
        // 新模型：仅隐私空间可设置密码（首访设密），任意文件夹不可再设为隐私
        if (f.getPrivacyMode() != PrivacyMode.PRIVATE) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "仅隐私空间可设置密码");
        }
        // 已设置过密码则禁止重置（暂不提供重置功能）
        if (f.getPrivacyPasswordHash() != null && !f.getPrivacyPasswordHash().isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "隐私空间已设置密码，暂不支持重置");
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "隐私密码不能为空");
        }

        // BCrypt 哈希后存储密码（隐私空间保持 PRIVATE）
        f.setPrivacyPasswordHash(passwordEncoder.encode(req.password()));
        updateById(f);

        // 更新密码后使旧访问会话失效
        privateFolderAccessService.remove(new LambdaQueryWrapper<PrivateFolderAccess>()
                .eq(PrivateFolderAccess::getFileItemId, id));

        return FileItemInfo.from(f);
    }

    @Override
    @Transactional
    public FileItemInfo removePrivacy(String id, String userId) {
        FileItem f = checkActive(id);
        // 清除隐私模式和密码哈希
        f.setPrivacyMode(PrivacyMode.NORMAL);
        f.setPrivacyPasswordHash("");
        updateById(f);

        // 清除所有访问会话——取消隐私后不再需要验证
        privateFolderAccessService.remove(new LambdaQueryWrapper<PrivateFolderAccess>()
                .eq(PrivateFolderAccess::getFileItemId, id));

        return FileItemInfo.from(f);
    }

    @Override
    @Transactional
    public Map<String, Object> verifyPrivacy(String id, VerifyPrivacyRequest req, String userId) {
        FileItem f = checkActive(id);
        // 验证目标文件夹确实是隐私模式
        if (f.getPrivacyMode() != PrivacyMode.PRIVATE) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "该文件夹未设置隐私保护");
        }

        // 校验隐私密码
        if (!passwordEncoder.matches(req.password(), f.getPrivacyPasswordHash())) {
            throw new BusinessException(ErrorCode.PRIVATE_PASSWORD_INVALID, "隐私密码错误");
        }

        // 清理过期会话
        privateFolderAccessService.remove(new LambdaQueryWrapper<PrivateFolderAccess>()
                .le(PrivateFolderAccess::getExpiresAt, LocalDateTime.now()));

        // 生成短期访问令牌（随机字节，哈希后存储）
        byte[] tokenBytes = new byte[ACCESS_TOKEN_BYTES];
        new SecureRandom().nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = passwordEncoder.encode(rawToken);

        PrivateFolderAccess access = new PrivateFolderAccess();
        access.setUserId(userId);
        access.setFileItemId(id);
        access.setAccessTokenHash(tokenHash);
        // 设置 30 分钟过期
        access.setExpiresAt(LocalDateTime.now().plusMinutes(ACCESS_SESSION_MINUTES));
        privateFolderAccessService.save(access);

        return Map.of(
                "accessToken", rawToken,
                "expiresAt", access.getExpiresAt().toString()
        );
    }

    // -------------------------------------------------------
    // 内部辅助方法
    // -------------------------------------------------------

    /**
     * 非管理员用户操作限定在主目录范围内——parentId 为空时重定向到该用户的主目录。
     * <p>
     * 管理员不受此限制，可在存储根目录任意位置操作。
     *
     * @return 有效 parentId（非管理员且原 parentId 为空时返回主目录 ID）
     */
    /**
     * 将用户文件操作限定到其主目录内。
     * <p>所有用户（包括 ADMIN）在 parentId 为空时默认定位到自己的 home 目录，
     * 避免文件直接落在存储根层级。导航到具体子目录后则以实际 parentId 为准。</p>
     *
     * @return 有效 parentId（原 parentId 为空时返回当前用户的主目录 ID）
     */
    private String scopeToHome(String rootId, String userId, String parentId) {
        User u = userMapper.selectById(userId);
        String username = u != null ? u.getUsername() : userId;
        String homeId = getOrCreateHomeFolder(rootId, userId, username);
        if (parentId == null || parentId.isBlank()) {
            return homeId;
        }
        return parentId;
    }

    /**
     * 校验用户对指定存储根目录的访问权限。
     * 非管理员必须持有对应 {@code user_storage_permission} 记录。
     */
    private void verifyAccess(String userId, String rootId) {
        if (userStoragePermissionService.getOne(new LambdaQueryWrapper<UserStoragePermission>()
                .eq(UserStoragePermission::getUserId, userId)
                .eq(UserStoragePermission::getStorageRootId, rootId)
                .isNull(UserStoragePermission::getFileItemId)
                .last("LIMIT 1")) == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问此存储");
        }
    }

    /**
     * 校验存储根目录是否可用于写入操作。
     * NAS 挂载的根目录离线时拒绝写入，但允许读取。
     */
    private void requireStorageAvailable(StorageRoot root) {
        if (root.getStatus() == StorageRootStatus.OFFLINE) {
            throw new BusinessException(ErrorCode.STORAGE_ROOT_OFFLINE,
                    i18nUtil.translate("存储根目录不可用（NAS 可能已离线）：") + root.getName());
        }
    }

    /**
     * 校验用户是否有权访问隐私文件夹。
     * <p>
     * 从指定的文件项开始向上遍历父目录链，
     * 如果链上存在 PRIVATE 模式的目录，则要求提供有效的访问令牌。
     * 访问令牌通过 {@code X-Privacy-Access-Token} 头传入，
     * 其哈希值与 {@code private_folder_access} 表中的记录比对。
     * <p>
     * ADMIN 用户仍需通过隐私验证（设计决策：管理员也不应绕过隐私密码）。
     *
     * @param fileItemId  当前要访问的文件项 ID（可为 null，表示根层级）
     * @param userId      访问用户 ID
     * @param accessToken 隐私访问令牌（可为 null 或空字符串）
     * @throws BusinessException PRIVATE_PASSWORD_REQUIRED 需要隐私密码但未提供访问令牌
     * @throws BusinessException PRIVATE_PASSWORD_INVALID 访问令牌无效或已过期
     */
    private void checkPrivacyAccess(String fileItemId, String userId, String accessToken) {
        // 向上遍历父目录链，查找隐私文件夹
        String cursorId = fileItemId;
        while (cursorId != null) {
            FileItem cursor = getById(cursorId);
            if (cursor == null) {
                break;
            }
            if (cursor.getPrivacyMode() == PrivacyMode.PRIVATE) {
                // 管理员访问隐私空间/隐私文件夹免密码
                User caller = userMapper.selectById(userId);
                if (caller != null && caller.getRole() == UserRole.ADMIN) {
                    return;
                }
                // 隐私空间尚未设置密码：要求先设置（首访流程）
                String hash = cursor.getPrivacyPasswordHash();
                if (hash == null || hash.isEmpty()) {
                    throw new BusinessException(ErrorCode.PRIVATE_SETUP_REQUIRED,
                            "隐私空间尚未设置密码，请先设置隐私密码");
                }
                // 发现隐私文件夹——要求提供有效访问令牌
                requireValidAccessToken(cursor.getId(), userId, accessToken);
                return; // 找到第一个隐私文件夹即返回（不需要继续向上）
            }
            cursorId = cursor.getParentId();
        }
    }

    /**
     * 要求用户提供有效的隐私访问令牌。
     * <p>
     * 查询 {@code private_folder_access} 表，查找该用户对该隐私文件夹的有效会话，
     * 然后比对传入的访问令牌与存储的哈希是否匹配。
     *
     * @param fileItemId  隐私文件夹 ID
     * @param userId      访问用户 ID
     * @param accessToken 客户端传入的访问令牌（明文）
     * @throws BusinessException PRIVATE_PASSWORD_REQUIRED 未提供访问令牌
     * @throws BusinessException PRIVATE_PASSWORD_INVALID 令牌无效或已过期
     */
    private void requireValidAccessToken(String fileItemId, String userId, String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.PRIVATE_PASSWORD_REQUIRED,
                    "此文件夹受隐私保护，请先验证隐私密码");
        }

        List<PrivateFolderAccess> sessions = privateFolderAccessService.list(new LambdaQueryWrapper<PrivateFolderAccess>()
                .eq(PrivateFolderAccess::getUserId, userId)
                .eq(PrivateFolderAccess::getFileItemId, fileItemId)
                .gt(PrivateFolderAccess::getExpiresAt, LocalDateTime.now())
                .orderByDesc(PrivateFolderAccess::getCreatedAt));
        boolean matched = false;
        for (PrivateFolderAccess s : sessions) {
            if (passwordEncoder.matches(accessToken, s.getAccessTokenHash())) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            throw new BusinessException(ErrorCode.PRIVATE_PASSWORD_INVALID,
                    "隐私访问令牌无效或已过期，请重新验证密码");
        }
    }

    /**
     * 清洗文件名：移除路径分隔符和空字节，防止路径穿越。
     */
    private String sanitize(String s) {
        return (s == null || s.isBlank()) ? "untitled" : s.replaceAll("[/\\\\:\0]", "_").trim();
    }

    /**
     * 根据父节点 ID 构建完整相对路径。
     */
    private String buildPath(String parentId, String name) {
        if (parentId == null || parentId.isBlank()) {
            return name;
        }
        FileItem p = getById(parentId);
        if (p == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "父文件夹不存在");
        }
        return p.getRelativePath() + "/" + name;
    }

    /**
     * 重命名时重新计算相对路径（替换最后一段名称）。
     */
    private String newRelPath(String old, String name) {
        int i = old.lastIndexOf('/');
        return i < 0 ? name : old.substring(0, i) + "/" + name;
    }

    /**
     * 计算文件的 SHA-256 哈希值。
     */
    private String hash(Path p) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(Files.readAllBytes(p));
            StringBuilder sb = new StringBuilder();
            for (byte v : b) {
                sb.append(String.format("%02x", v));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String blankNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String parentOrNull(String s) {
        return blankNull(s);
    }

    /**
     * 子项查询公共条件：根目录 + 父目录（空视为根）+ 可选所有者/状态筛选，排序「目录优先、名称升序」。
     */
    private LambdaQueryWrapper<FileItem> childrenWrapper(String rootId, String parentId,
                                                         String ownerUserId, String status, String sort, String dir) {
        LambdaQueryWrapper<FileItem> wrapper = new LambdaQueryWrapper<FileItem>()
                .eq(FileItem::getStorageRootId, rootId)
                .eq(ownerUserId != null, FileItem::getOwnerUserId, ownerUserId)
                .eq(status != null, FileItem::getStatus, status)
                .orderByDesc(FileItem::getItemType);  // 目录优先：任何排序下都保持目录在前
        applySort(wrapper, sort, dir);
        String p = parentOrNull(parentId);
        if (p == null) {
            wrapper.isNull(FileItem::getParentId);
        } else {
            wrapper.eq(FileItem::getParentId, p);
        }
        return wrapper;
    }

    /** 二级排序：按 sort 字段 + dir 方向；非法 sort 回落名称 */
    private void applySort(LambdaQueryWrapper<FileItem> wrapper, String sort, String dir) {
        boolean desc = "desc".equals(dir);
        if ("createdAt".equals(sort)) {
            if (desc) wrapper.orderByDesc(FileItem::getCreatedAt); else wrapper.orderByAsc(FileItem::getCreatedAt);
        } else if ("size".equals(sort)) {
            if (desc) wrapper.orderByDesc(FileItem::getSizeBytes); else wrapper.orderByAsc(FileItem::getSizeBytes);
        } else {
            if (desc) wrapper.orderByDesc(FileItem::getName); else wrapper.orderByAsc(FileItem::getName);
        }
    }

    /** 批量统计直接活跃子项数：parent_id -> count（供列表目录行展示「N 项」） */
    private Map<String, Long> childCountsByParents(List<String> parentIds) {
        Map<String, Long> result = new java.util.HashMap<>();
        for (Map<String, Object> row : baseMapper.countChildrenByParents(parentIds)) {
            Object pid = row.get("parent_id");
            Object cnt = row.get("cnt");
            if (pid != null && cnt != null) {
                result.put(pid.toString(), ((Number) cnt).longValue());
            }
        }
        return result;
    }

    /** 记录「上次打开时间」：显式 SET updated_at = updated_at，利用 MySQL ON UPDATE CURRENT_TIMESTAMP「显式赋值当前值时不触发」的规则，避免打开≠修改 */
    private void touchLastOpened(String fileItemId) {
        lambdaUpdate().eq(FileItem::getId, fileItemId)
                .set(FileItem::getLastOpenedAt, LocalDateTime.now())
                .setSql("updated_at = updated_at")
                .update();
    }

    /** 进入目录时记录「上次打开时间」：仅当目标确为目录且调用者有权访问（无权静默跳过，不改变现有列表行为） */
    private void touchFolderOpen(String folderId, String userId, boolean isAdmin) {
        if (folderId == null) {
            return;
        }
        FileItem folder = getById(folderId);
        if (folder == null || folder.getItemType() != ItemType.DIRECTORY) {
            return;
        }
        if (!isAdmin && !userId.equals(folder.getOwnerUserId())) {
            return;
        }
        touchLastOpened(folderId);
    }

    /**
     * 按存储根目录和相对路径精确查找活跃文件项。
     */
    private FileItem findByPath(String rootId, String relativePath) {
        return getOne(new LambdaQueryWrapper<FileItem>()
                .eq(FileItem::getStorageRootId, rootId)
                .eq(FileItem::getRelativePath, relativePath)
                .eq(FileItem::getStatus, "ACTIVE")
                .last("LIMIT 1"));
    }

    /**
     * 根据 ID 检查文件项是否存在且为 ACTIVE 状态。
     */
    private FileItem checkActive(String id) {
        FileItem f = getById(id);
        if (f == null || f.getStatus() != FileItemStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件项不存在");
        }
        return f;
    }

    /**
     * 验证非管理员用户对文件的操作权限。
     * 非管理员只能操作自己拥有的文件。
     */
    private void checkOwnership(FileItem item, String userId, boolean isAdmin) {
        if (!isAdmin && !userId.equals(item.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此文件");
        }
    }

    /**
     * 获取或创建用户的个人主目录（如百度网盘的"我的文件"）。
     * <p>
     * 在主目录下以用户名创建一个文件夹，作为该用户在文件中心的入口。
     * 已存在则不重复创建。
     *
     * @param rootId   存储根目录 ID
     * @param userId   用户 ID
     * @param username 用户名（用作主目录名称）
     * @return 主目录的 FileItem ID
     */
    /** 隐私空间固定文件夹名（主目录直接子目录，用户不可在其路径下新建同名文件夹） */
    private static final String PRIVACY_SPACE_NAME = "隐私空间";

    private String getOrCreateHomeFolder(String rootId, String userId, String username) {
        String relativePath = username;
        FileItem home = findByPath(rootId, relativePath);
        if (home == null || home.getStatus() != FileItemStatus.ACTIVE) {
            StorageRoot root = storageService.getByIdOrThrow(rootId);
            Path homePath = storageService.resolveRootPath(root).resolve(relativePath).normalize();
            storageService.verifyPathInRoot(root, homePath);

            try {
                Files.createDirectories(homePath);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED,
                        i18nUtil.translate("无法创建用户主目录：") + e.getMessage());
            }

            home = new FileItem();
            home.setStorageRootId(rootId);
            home.setParentId(null);
            home.setOwnerUserId(userId);
            home.setName(username);
            home.setRelativePath(relativePath);
            home.setItemType(ItemType.DIRECTORY);
            home.setSizeBytes(0L);
            home.setPrivacyMode(PrivacyMode.NORMAL);
            home.setStatus(FileItemStatus.ACTIVE);
            save(home);

            log.info("已为用户 {} 创建主目录: {} (root={})", username, relativePath, rootId);
        }

        // 同时确保该用户存在「隐私空间」（PRIVATE、密码未设置，首访时设置）
        ensurePrivacySpace(rootId, home, userId);
        return home.getId();
    }

    /** 确保用户主目录下有「隐私空间」子目录；已存在则跳过 */
    private void ensurePrivacySpace(String rootId, FileItem home, String userId) {
        String homeId = home.getId();
        FileItem space = getOne(new LambdaQueryWrapper<FileItem>()
                .eq(FileItem::getStorageRootId, rootId)
                .eq(FileItem::getParentId, homeId)
                .eq(FileItem::getName, PRIVACY_SPACE_NAME)
                .eq(FileItem::getStatus, FileItemStatus.ACTIVE)
                .last("LIMIT 1"));
        if (space != null) {
            return;
        }

        StorageRoot root = storageService.getByIdOrThrow(rootId);
        String rel = home.getRelativePath() + "/" + PRIVACY_SPACE_NAME;
        Path spacePath = storageService.resolveRootPath(root).resolve(rel).normalize();
        storageService.verifyPathInRoot(root, spacePath);
        try {
            Files.createDirectories(spacePath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED,
                    i18nUtil.translate("无法创建隐私空间：") + e.getMessage());
        }

        FileItem spaceItem = new FileItem();
        spaceItem.setStorageRootId(rootId);
        spaceItem.setParentId(homeId);
        spaceItem.setOwnerUserId(userId);
        spaceItem.setName(PRIVACY_SPACE_NAME);
        spaceItem.setRelativePath(rel);
        spaceItem.setItemType(ItemType.DIRECTORY);
        spaceItem.setSizeBytes(0L);
        // 隐私空间恒为 PRIVATE，密码未设置（空哈希），首访时通过 setPrivacy 设置
        spaceItem.setPrivacyMode(PrivacyMode.PRIVATE);
        spaceItem.setPrivacyPasswordHash("");
        spaceItem.setStatus(FileItemStatus.ACTIVE);
        save(spaceItem);

        log.info("已为用户 {} 创建隐私空间: {}", userId, rel);
    }

    /** 判断是否为隐私空间：名称为固定名，且父目录为根级目录（用户主目录） */
    private boolean isPrivacySpace(FileItem f) {
        if (f.getParentId() == null || !PRIVACY_SPACE_NAME.equals(f.getName())) {
            return false;
        }
        FileItem parent = getById(f.getParentId());
        return parent != null && parent.getParentId() == null;
    }

    /** 根级主目录与隐私空间不可重命名/删除/设隐私，管理员也不例外；action 用于错误文案 */
    private void assertMutable(FileItem f, String action) {
        if (f.getParentId() == null) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "主目录不可" + action);
        }
        if (isPrivacySpace(f)) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "隐私空间不可" + action);
        }
    }

    /** 隐私文件夹本身不支持重命名/删除（需先移除隐私再操作）；不用于 setPrivacy（改密码/设隐私） */
    private void assertNotPrivate(FileItem f, String action) {
        if (f.getPrivacyMode() == PrivacyMode.PRIVATE) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "隐私文件夹需先移除隐私后再" + action);
        }
    }

    // ---- 预览与进度 ----

    @Override
    public Resource previewFile(String fileId, String userId, boolean isAdmin, String privacyAccessToken) {
        // 复用 downloadFile 的校验逻辑（存在性、类型、权限、路径穿越）
        Resource original = downloadFile(fileId, userId, isAdmin, privacyAccessToken);

        // Office 文件自动转换为 PDF 后返回
        FileItem file = getById(fileId);
        if (file != null && convertService.needsConversion(file)) {
            Path pdfPath = convertService.convertToPdf(file);
            if (pdfPath != null && Files.exists(pdfPath)) {
                return new FileSystemResource(pdfPath);
            }
        }
        return original;
    }

    @Override
    public Map<String, Object> getProgress(String fileId, String userId) {
        PlaybackProgress p = playbackProgressService.getOne(new LambdaQueryWrapper<PlaybackProgress>()
                .eq(PlaybackProgress::getUserId, userId)
                .eq(PlaybackProgress::getFileItemId, fileId)
                .last("LIMIT 1"));
        if (p == null) return null;
        return Map.of(
                "fileId", p.getFileItemId(),
                "positionType", p.getPositionType(),
                "positionValue", p.getPositionValue(),
                "updatedAt", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : ""
        );
    }

    @Override
    public void saveProgress(String fileId, String userId, String positionType, Double positionValue) {
        PlaybackProgress existing = playbackProgressService.getOne(new LambdaQueryWrapper<PlaybackProgress>()
                .eq(PlaybackProgress::getUserId, userId)
                .eq(PlaybackProgress::getFileItemId, fileId)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setPositionType(positionType != null ? positionType : "SECONDS");
            existing.setPositionValue(positionValue != null ? positionValue : 0);
            playbackProgressService.updateById(existing);
        } else {
            PlaybackProgress p = new PlaybackProgress();
            p.setUserId(userId);
            p.setFileItemId(fileId);
            p.setPositionType(positionType != null ? positionType : "SECONDS");
            p.setPositionValue(positionValue != null ? positionValue : 0);
            playbackProgressService.save(p);
        }
    }
}
