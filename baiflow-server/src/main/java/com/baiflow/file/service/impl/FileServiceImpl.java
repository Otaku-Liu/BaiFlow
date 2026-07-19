package com.baiflow.file.service.impl;

import com.baiflow.common.entity.ApiResponse.Code;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.file.dto.request.CreateFolderRequest;
import com.baiflow.file.dto.request.MoveRequest;
import com.baiflow.file.dto.request.RenameRequest;
import com.baiflow.file.dto.request.SetPrivacyRequest;
import com.baiflow.file.dto.request.VerifyPrivacyRequest;
import com.baiflow.file.dto.response.FileItemInfo;
import com.baiflow.file.entity.FileItem;
import com.baiflow.file.entity.PrivateFolderAccess;
import com.baiflow.file.enums.FileItemStatus;
import com.baiflow.file.enums.ItemType;
import com.baiflow.file.enums.PrivacyMode;
import com.baiflow.file.mapper.FileItemMapper;
import com.baiflow.file.mapper.PrivateFolderAccessMapper;
import com.baiflow.file.service.FileService;
import com.baiflow.storage.entity.StorageRoot;
import com.baiflow.storage.enums.StorageRootStatus;
import com.baiflow.storage.mapper.UserStoragePermissionMapper;
import com.baiflow.storage.service.StorageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.util.List;
import java.util.Map;

/**
 * 文件服务实现 — 所有文件操作均强制遵循存储根目录边界约束和用户权限校验。
 */
@Service
public class FileServiceImpl implements FileService {

    private static final int ACCESS_TOKEN_BYTES = 32;
    /**
     * 隐私文件夹访问会话有效期（分钟）
     */
    private static final int ACCESS_SESSION_MINUTES = 30;

    private final FileItemMapper fileItemMapper;
    private final StorageService storageService;
    private final UserStoragePermissionMapper permMapper;
    private final PrivateFolderAccessMapper pfaMapper;
    private final PasswordEncoder passwordEncoder;

    public FileServiceImpl(FileItemMapper fileItemMapper, StorageService storageService,
                           UserStoragePermissionMapper permMapper,
                           PrivateFolderAccessMapper pfaMapper,
                           PasswordEncoder passwordEncoder) {
        this.fileItemMapper = fileItemMapper;
        this.storageService = storageService;
        this.permMapper = permMapper;
        this.pfaMapper = pfaMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public IPage<FileItemInfo> listFiles(String rootId, String parentId, int page, int size,
                                         String userId, boolean isAdmin, String privacyAccessToken) {
        // 非管理员需校验存储访问权限
        if (!isAdmin) {
            verifyAccess(userId, rootId);
        }

        storageService.getByIdOrThrow(rootId);

        // 进入文件夹前检查隐私保护（向上遍历父目录链）
        checkPrivacyAccess(parentId, userId, privacyAccessToken);

        List<FileItem> items = fileItemMapper.selectChildren(
                rootId, parentOrNull(parentId), FileItemStatus.ACTIVE.name());

        // 内存分页
        int total = items.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<FileItemInfo> recs = (from < total ? items.subList(from, to) : List.<FileItem>of())
                .stream().map(FileItemInfo::from).toList();
        IPage<FileItemInfo> r = new Page<>(page, size, total);
        r.setRecords(recs);
        return r;
    }

    @Override
    @Transactional
    public FileItemInfo uploadFile(String rootId, String parentId, MultipartFile file,
                                   String userId, String privacyAccessToken) {
        StorageRoot root = storageService.getByIdOrThrow(rootId);
        // NAS 离线时禁止写入
        requireStorageAvailable(root);

        // 上传到隐私文件夹内需先验证隐私密码
        checkPrivacyAccess(parentId, userId, privacyAccessToken);

        // 清洗文件名，构建相对路径
        String safe = sanitize(file.getOriginalFilename());
        String rel = buildPath(parentId, safe);

        // 检查同名文件
        if (fileItemMapper.selectByPath(rootId, rel) != null) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "文件已存在：" + safe);
        }

        // 解析目标路径并执行路径穿越校验
        Path rootPath = storageService.resolveRootPath(root);
        Path target = rootPath.resolve(rel).normalize();
        storageService.verifyPathInRoot(root, target);

        // 确保父目录存在
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "无法创建父目录：" + e.getMessage());
        }

        // 写入文件并计算 SHA-256 哈希
        String sha;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            sha = hash(target);
        } catch (IOException e) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "文件写入失败：" + e.getMessage());
        }

        // 持久化元数据（磁盘写入成功后才写库）
        FileItem f = new FileItem();
        f.setStorageRootId(rootId);
        f.setParentId(blankNull(parentId));
        f.setOwnerUserId(userId);
        f.setName(safe);
        f.setRelativePath(rel);
        f.setItemType(ItemType.FILE);
        f.setSizeBytes(file.getSize());
        f.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        f.setHashSha256(sha);
        f.setPrivacyMode(PrivacyMode.NORMAL);
        f.setStatus(FileItemStatus.ACTIVE);
        fileItemMapper.insert(f);

        return FileItemInfo.from(f);
    }

    @Override
    public Resource downloadFile(String fileId, String userId, boolean isAdmin, String privacyAccessToken) {
        // 检查元数据是否存在且为 FILE（不支持直接下载目录）
        FileItem f = fileItemMapper.selectById(fileId);
        if (f == null || f.getStatus() != FileItemStatus.ACTIVE) {
            throw new BusinessException(Code.NOT_FOUND, "文件不存在");
        }
        if (f.getItemType() != ItemType.FILE) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "无法下载文件夹");
        }

        if (!isAdmin) {
            verifyAccess(userId, f.getStorageRootId());
        }

        // 下载隐私文件夹内的文件需验证隐私密码
        checkPrivacyAccess(f.getParentId(), userId, privacyAccessToken);

        // 解析磁盘路径，校验边界
        StorageRoot root = storageService.getByIdOrThrow(f.getStorageRootId());
        Path fp = storageService.resolveRootPath(root).resolve(f.getRelativePath()).normalize();
        storageService.verifyPathInRoot(root, fp);

        if (!Files.exists(fp)) {
            throw new BusinessException(Code.NOT_FOUND, "磁盘文件不存在");
        }
        return new FileSystemResource(fp);
    }

    @Override
    @Transactional
    public FileItemInfo createFolder(CreateFolderRequest req, String userId, String privacyAccessToken) {
        StorageRoot root = storageService.getByIdOrThrow(req.storageRootId());
        // NAS 离线时禁止写入
        requireStorageAvailable(root);

        // 在隐私文件夹内创建子文件夹需先验证隐私密码
        checkPrivacyAccess(req.parentId(), userId, privacyAccessToken);

        String safe = sanitize(req.name());
        String rel = buildPath(req.parentId(), safe);

        if (fileItemMapper.selectByPath(req.storageRootId(), rel) != null) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "文件夹已存在：" + safe);
        }

        // 解析并校验路径，在磁盘上创建目录
        Path target = storageService.resolveRootPath(root).resolve(rel).normalize();
        storageService.verifyPathInRoot(root, target);
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "无法创建文件夹：" + e.getMessage());
        }

        // 持久化元数据
        FileItem f = new FileItem();
        f.setStorageRootId(req.storageRootId());
        f.setParentId(blankNull(req.parentId()));
        f.setOwnerUserId(userId);
        f.setName(safe);
        f.setRelativePath(rel);
        f.setItemType(ItemType.DIRECTORY);
        f.setSizeBytes(0L);
        f.setPrivacyMode(PrivacyMode.NORMAL);
        f.setStatus(FileItemStatus.ACTIVE);
        fileItemMapper.insert(f);

        return FileItemInfo.from(f);
    }

    @Override
    @Transactional
    public FileItemInfo rename(String id, RenameRequest req, String userId, boolean isAdmin,
                               String privacyAccessToken) {
        FileItem f = checkActive(id);
        if (!isAdmin) {
            verifyAccess(userId, f.getStorageRootId());
        }
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
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "重命名失败：" + e.getMessage());
        }

        // 更新元数据
        f.setName(nn);
        f.setRelativePath(nr);
        fileItemMapper.updateById(f);
        return FileItemInfo.from(f);
    }

    @Override
    @Transactional
    public FileItemInfo move(String id, MoveRequest req, String userId, boolean isAdmin,
                             String privacyAccessToken) {
        FileItem f = checkActive(id);
        if (!isAdmin) {
            verifyAccess(userId, f.getStorageRootId());
        }
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
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "移动失败：" + e.getMessage());
        }

        // 更新元数据中的存储根和父节点
        f.setStorageRootId(req.targetStorageRootId());
        f.setParentId(blankNull(req.targetParentId()));
        f.setRelativePath(nr);
        fileItemMapper.updateById(f);
        return FileItemInfo.from(f);
    }

    @Override
    @Transactional
    public void delete(String id, String userId, boolean isAdmin, String privacyAccessToken) {
        FileItem f = checkActive(id);
        if (!isAdmin) {
            verifyAccess(userId, f.getStorageRootId());
        }
        // NAS 离线时禁止写入
        requireStorageAvailable(storageService.getByIdOrThrow(f.getStorageRootId()));

        // 删除隐私文件夹内的项目需验证隐私密码
        checkPrivacyAccess(f.getParentId(), userId, privacyAccessToken);

        // 先标记元数据为已删除（软删除），再删除磁盘文件
        // 如果磁盘删除失败，元数据已安全标记，后续可由清理任务修复
        f.setStatus(FileItemStatus.DELETED);
        f.setDeletedAt(LocalDateTime.now());
        fileItemMapper.updateById(f);

        StorageRoot root = storageService.getByIdOrThrow(f.getStorageRootId());
        Path p = storageService.resolveRootPath(root).resolve(f.getRelativePath()).normalize();
        storageService.verifyPathInRoot(root, p);

        try {
            if (Files.isDirectory(p)) {
                // 递归删除目录树
                try (var s = Files.walk(p)) {
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
        // 仅目录可设置为隐私模式
        if (f.getItemType() != ItemType.DIRECTORY) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "仅文件夹可设置为隐私模式");
        }

        // BCrypt 哈希后存储密码
        f.setPrivacyMode(PrivacyMode.PRIVATE);
        f.setPrivacyPasswordHash(passwordEncoder.encode(req.password()));
        fileItemMapper.updateById(f);

        // 更新密码后使旧访问会话失效
        pfaMapper.deleteByFileItemId(id);

        return FileItemInfo.from(f);
    }

    @Override
    @Transactional
    public FileItemInfo removePrivacy(String id, String userId) {
        FileItem f = checkActive(id);
        // 清除隐私模式和密码哈希
        f.setPrivacyMode(PrivacyMode.NORMAL);
        f.setPrivacyPasswordHash("");
        fileItemMapper.updateById(f);

        // 清除所有访问会话——取消隐私后不再需要验证
        pfaMapper.deleteByFileItemId(id);

        return FileItemInfo.from(f);
    }

    @Override
    @Transactional
    public Map<String, Object> verifyPrivacy(String id, VerifyPrivacyRequest req, String userId) {
        FileItem f = checkActive(id);
        // 验证目标文件夹确实是隐私模式
        if (f.getPrivacyMode() != PrivacyMode.PRIVATE) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "该文件夹未设置隐私保护");
        }

        // 校验隐私密码
        if (!passwordEncoder.matches(req.password(), f.getPrivacyPasswordHash())) {
            throw new BusinessException(Code.PRIVATE_PASSWORD_INVALID, "隐私密码错误");
        }

        // 清理过期会话
        pfaMapper.deleteExpired();

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
        pfaMapper.insert(access);

        return Map.of(
                "accessToken", rawToken,
                "expiresAt", access.getExpiresAt().toString()
        );
    }

    // -------------------------------------------------------
    // 内部辅助方法
    // -------------------------------------------------------

    /**
     * 校验用户对指定存储根目录的访问权限。
     * 非管理员必须持有对应 {@code user_storage_permission} 记录。
     */
    private void verifyAccess(String userId, String rootId) {
        if (permMapper.selectByUserAndRoot(userId, rootId) == null) {
            throw new BusinessException(Code.FORBIDDEN, "无权访问此存储");
        }
    }

    /**
     * 校验存储根目录是否可用于写入操作。
     * NAS 挂载的根目录离线时拒绝写入，但允许读取。
     */
    private void requireStorageAvailable(StorageRoot root) {
        if (root.getStatus() == StorageRootStatus.OFFLINE) {
            throw new BusinessException(Code.STORAGE_ROOT_OFFLINE,
                    "存储根目录不可用（NAS 可能已离线）：" + root.getName());
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
            FileItem cursor = fileItemMapper.selectById(cursorId);
            if (cursor == null) {
                break;
            }
            if (cursor.getPrivacyMode() == PrivacyMode.PRIVATE) {
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
            throw new BusinessException(Code.PRIVATE_PASSWORD_REQUIRED,
                    "此文件夹受隐私保护，请先验证隐私密码");
        }

        List<PrivateFolderAccess> sessions = pfaMapper.selectValidByUserAndFolder(userId, fileItemId);
        boolean matched = false;
        for (PrivateFolderAccess s : sessions) {
            if (passwordEncoder.matches(accessToken, s.getAccessTokenHash())) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            throw new BusinessException(Code.PRIVATE_PASSWORD_INVALID,
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
        FileItem p = fileItemMapper.selectById(parentId);
        if (p == null) {
            throw new BusinessException(Code.NOT_FOUND, "父文件夹不存在");
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
     * 根据 ID 检查文件项是否存在且为 ACTIVE 状态。
     */
    private FileItem checkActive(String id) {
        FileItem f = fileItemMapper.selectById(id);
        if (f == null || f.getStatus() != FileItemStatus.ACTIVE) {
            throw new BusinessException(Code.NOT_FOUND, "文件项不存在");
        }
        return f;
    }
}
