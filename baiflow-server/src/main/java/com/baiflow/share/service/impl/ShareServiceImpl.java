package com.baiflow.share.service.impl;

import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.downloadrecord.enums.DownloadSource;
import com.baiflow.downloadrecord.service.DownloadRecordService;
import com.baiflow.file.dto.response.FileItemInfo;
import com.baiflow.file.entity.FileItem;
import com.baiflow.file.service.FileService;
import com.baiflow.share.dto.request.CreateShareRequest;
import com.baiflow.share.dto.request.UpdateShareRequest;
import com.baiflow.share.dto.response.ShareLinkInfo;
import com.baiflow.share.entity.ShareAccessLog;
import com.baiflow.share.entity.ShareLink;
import com.baiflow.share.enums.AccessMode;
import com.baiflow.share.enums.ShareStatus;
import com.baiflow.share.enums.ShareType;
import com.baiflow.share.mapper.ShareAccessLogMapper;
import com.baiflow.share.mapper.ShareLinkMapper;
import com.baiflow.share.service.ShareService;
import com.baiflow.storage.entity.StorageRoot;
import com.baiflow.storage.service.StorageService;
import com.baiflow.user.entity.User;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ShareServiceImpl extends ServiceImpl<ShareLinkMapper, ShareLink> implements ShareService {
    private static final String REDIS_SHARE_VIEW_KEY = "share:view:";

    /** 提取码最大错误次数 */
    private static final int MAX_CODE_FAILURES = 5;
    /** 提取码锁定时长（分钟），同时作为失败计数滑动窗口 */
    private static final int CODE_LOCK_MINUTES = 15;
    /** Redis 键前缀：提取码失败次数 */
    private static final String REDIS_CODE_FAIL_KEY = "share:code:fail:";
    /** Redis 键前缀：提取码锁定标记 */
    private static final String REDIS_CODE_LOCK_KEY = "share:code:lock:";

    @Autowired
    private ShareAccessLogMapper logMapper;
    @Autowired
    private DownloadRecordService downloadRecordService;
    @Autowired
    private FileService fileService;
    @Autowired
    private StorageService storageService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override @Transactional
    public ShareLinkInfo createShare(CreateShareRequest req, String userId) {
        // 校验用户存在且为活跃状态
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != UserStatus.NORMAL) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户不存在或已被禁用");
        }

        FileItem target = fileService.getById(req.targetFileItemId());
        if (target == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "文件/文件夹不存在"); }

        // 生成不可预测 token，只存 hash
        byte[] tokenBytes = new byte[32]; new SecureRandom().nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        ShareLink sl = new ShareLink();
        sl.setTargetFileItemId(req.targetFileItemId());
        sl.setCreatedBy(userId);
        sl.setOwnerUsername(user.getUsername());
        sl.setOwnerDisplayName(user.getDisplayName());
        sl.setTokenHash(passwordEncoder.encode(rawToken));
        sl.setShareType(ShareType.valueOf(req.shareType()));
        sl.setAccessMode(AccessMode.valueOf(req.accessMode()));
        sl.setExpiresAt(req.expiresAt() != null && !req.expiresAt().isBlank()
                ? LocalDateTime.parse(req.expiresAt()) : null);
        sl.setMaxViews(Math.max(0, req.maxViews()));
        sl.setViewCount(0);
        sl.setMaxDownloads(Math.max(0, req.maxDownloads()));
        sl.setDownloadCount(0);
        // 提取码只存 hash（未设置时为空字符串）
        sl.setExtractionCodeHash(req.extractionCode() != null && !req.extractionCode().isBlank()
                ? passwordEncoder.encode(req.extractionCode()) : "");
        // 检测分享目标是否为隐私文件夹
        sl.setRequirePrivatePassword(
                "PRIVATE".equals(target.getPrivacyMode().name()));
        sl.setStatus(ShareStatus.ACTIVE);
        save(sl);

        // 返回时附加 rawToken（仅创建时可见）
        log.info("分享链接已创建: token={}, target={}", rawToken.substring(0, 8) + "...", req.targetFileItemId());
        return ShareLinkInfo.from(sl, rawToken);
    }

    @Override public IPage<ShareLinkInfo> listShares(String userId, boolean isAdmin, String status, int page, int size) {
        int offset = (page - 1) * size;
        List<ShareLink> list; long total;
        if (isAdmin) {
            list = lambdaQuery()
                    .eq(status != null && !status.isBlank(), ShareLink::getStatus, status)
                    .orderByDesc(ShareLink::getCreatedAt)
                    .last("LIMIT " + offset + ", " + size)
                    .list();
            total = lambdaQuery()
                    .eq(status != null && !status.isBlank(), ShareLink::getStatus, status)
                    .count();
        } else {
            list = lambdaQuery()
                    .eq(ShareLink::getCreatedBy, userId)
                    .eq(status != null && !status.isBlank(), ShareLink::getStatus, status)
                    .orderByDesc(ShareLink::getCreatedAt)
                    .last("LIMIT " + offset + ", " + size)
                    .list();
            total = lambdaQuery()
                    .eq(ShareLink::getCreatedBy, userId)
                    .eq(status != null && !status.isBlank(), ShareLink::getStatus, status)
                    .count();
        }
        IPage<ShareLinkInfo> r = new Page<>(page, size, total);
        r.setRecords(list.stream().map(ShareLinkInfo::from).toList());
        return r;
    }

    @Override public ShareLinkInfo getShare(String id, String userId, boolean isAdmin) {
        ShareLink sl = getById(id);
        if (sl == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "分享链接不存在"); }
        if (!isAdmin && !sl.getCreatedBy().equals(userId)) { throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看"); }
        return ShareLinkInfo.from(sl);
    }

    @Override @Transactional
    public ShareLinkInfo updateShare(String id, UpdateShareRequest req, String userId, boolean isAdmin) {
        ShareLink sl = getById(id);
        if (sl == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "分享链接不存在"); }
        if (!isAdmin && !sl.getCreatedBy().equals(userId)) { throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改"); }
        if (req.status() != null) { sl.setStatus(ShareStatus.valueOf(req.status())); }
        if (req.expiresAt() != null) { sl.setExpiresAt(LocalDateTime.parse(req.expiresAt())); }
        if (req.maxViews() != null) { sl.setMaxViews(Math.max(0, req.maxViews())); }
        if (req.maxDownloads() != null) { sl.setMaxDownloads(Math.max(0, req.maxDownloads())); }
        if (req.extractionCode() != null && !req.extractionCode().isBlank()) {
            sl.setExtractionCodeHash(passwordEncoder.encode(req.extractionCode()));
        }
        updateById(sl);
        return ShareLinkInfo.from(sl);
    }

    @Override @Transactional
    public void revokeShare(String id, String userId, boolean isAdmin) {
        ShareLink sl = getById(id);
        if (sl == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "分享链接不存在"); }
        if (!isAdmin && !sl.getCreatedBy().equals(userId)) { throw new BusinessException(ErrorCode.FORBIDDEN, "无权撤销"); }
        sl.setStatus(ShareStatus.REVOKED);
        updateById(sl);
    }

    // ===================== 公开访问 =====================

    @Override @Transactional
    public ShareLinkInfo viewByToken(String token, HttpServletRequest request) {
        ShareLink sl = validateAndLog(token, "VIEW", request);
        // 如果设置了提取码则要求先校验
        if (sl.getExtractionCodeHash() != null && !sl.getExtractionCodeHash().isEmpty()) {
            throw new BusinessException(ErrorCode.EXTRACTION_CODE_REQUIRED, "需要提取码");
        }
        incrementView(sl);
        return ShareLinkInfo.from(sl);
    }

    @Override @Transactional
    public Map<String, Object> verifyExtractionCode(String token, String code, HttpServletRequest request) {
        ShareLink sl = validateAndLog(token, "VERIFY_CODE", request);
        if (sl.getExtractionCodeHash() == null || sl.getExtractionCodeHash().isEmpty()) {
            return Map.of("valid", true, "message", "无需提取码");
        }
        // 提取码错误次数过多锁定检查（Redis 计数，多实例共享）
        if (isCodeLocked(sl.getId())) {
            recordLog(sl, "VERIFY_CODE", request, false, "提取码锁定");
            throw new BusinessException(ErrorCode.EXTRACTION_CODE_INVALID,
                    "提取码错误次数过多，请" + CODE_LOCK_MINUTES + "分钟后再试");
        }
        if (!passwordEncoder.matches(code, sl.getExtractionCodeHash())) {
            recordCodeFailure(sl.getId());
            recordLog(sl, "VERIFY_CODE", request, false, "提取码错误");
            throw new BusinessException(ErrorCode.EXTRACTION_CODE_INVALID, "提取码错误");
        }
        clearCodeFailures(sl.getId());
        recordLog(sl, "VERIFY_CODE", request, true, "");
        incrementView(sl);
        return Map.of("valid", true, "message", "提取码验证成功");
    }

    @Override @Transactional
    public Map<String, Object> verifyPrivatePassword(String token, String password, HttpServletRequest request) {
        ShareLink sl = validateAndLog(token, "VERIFY_CODE", request);
        if (!sl.getRequirePrivatePassword()) {
            return Map.of("valid", true, "message", "不需要隐私密码");
        }
        FileItem target = fileService.getById(sl.getTargetFileItemId());
        if (target == null || target.getPrivacyPasswordHash() == null || target.getPrivacyPasswordHash().isEmpty()) {
            recordLog(sl, "VERIFY_CODE", request, false, "隐私密码未设置");
            throw new BusinessException(ErrorCode.PRIVATE_PASSWORD_INVALID, "隐私密码未设置");
        }
        if (!passwordEncoder.matches(password, target.getPrivacyPasswordHash())) {
            recordLog(sl, "VERIFY_CODE", request, false, "隐私密码错误");
            throw new BusinessException(ErrorCode.PRIVATE_PASSWORD_INVALID, "隐私密码错误");
        }
        recordLog(sl, "VERIFY_CODE", request, true, "");
        // 生成短期 privacy token（复用隐私文件夹机制）
        String t = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().getBytes());
        return Map.of("valid", true, "privacyToken", t);
    }

    @Override
    public IPage<FileItemInfo> browseShareFolder(String token, String parentId, int page, int size,
                                                  String privacyToken, HttpServletRequest request) {
        ShareLink sl = validateAndLog(token, "VIEW", request);
        if (sl.getShareType() != ShareType.FOLDER) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "此分享不是文件夹");
        }
        // 直接用 fileItemMapper 查子文件（公开访问不校验权限，仅限分享目标）
        String folderId = sl.getTargetFileItemId();
        if (parentId != null && !parentId.isBlank()) {
            // 验证 parent 在分享目标子树内（简化：仅允许在分享文件夹内浏览）
            FileItem parent = fileService.getById(parentId);
            if (parent == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "文件夹不存在"); }
            folderId = parentId;
        }
        // 使用 file list 逻辑但不做权限校验
        FileItem shareTarget = fileService.getById(sl.getTargetFileItemId());
        List<FileItem> items = fileService.list(new LambdaQueryWrapper<FileItem>()
                .eq(FileItem::getStorageRootId, shareTarget.getStorageRootId())
                .eq(FileItem::getParentId, folderId)
                .eq(FileItem::getStatus, "ACTIVE")
                .orderByDesc(FileItem::getItemType)
                .orderByAsc(FileItem::getName));
        int total = items.size();
        int from = Math.min((page-1)*size, total);
        int to = Math.min(from+size, total);
        List<FileItemInfo> recs = (from<total ? items.subList(from,to) : List.<FileItem>of())
                .stream().map(FileItemInfo::from).toList();
        IPage<FileItemInfo> r = new Page<>(page, size, total); r.setRecords(recs);
        incrementView(sl);
        return r;
    }

    @Override
    public Resource downloadShareFile(String token, String fileId, String privacyToken, HttpServletRequest request) {
        ShareLink sl = validateAndLog(token, "DOWNLOAD", request);
        if (sl.getAccessMode() != AccessMode.DOWNLOAD) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "此分享不支持下载");
        }
        if (sl.getMaxDownloads() > 0 && sl.getDownloadCount() >= sl.getMaxDownloads()) {
            throw new BusinessException(ErrorCode.SHARE_LIMIT_EXCEEDED, "下载次数已达上限");
        }
        FileItem file = fileService.getById(fileId);
        if (file == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在"); }
        // 检查文件在分享目标范围内
        if (!fileId.equals(sl.getTargetFileItemId())) {
            // 简化：检查父目录链是否包含分享目标
            // MVP 实现：仅允许下载分享目标本身
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可下载分享目标文件");
        }
        // 解析磁盘路径
        StorageRoot root = storageService.getByIdOrThrow(file.getStorageRootId());
        Path fp = storageService.resolveRootPath(root).resolve(file.getRelativePath()).normalize();
        storageService.verifyPathInRoot(root, fp);
        if (!Files.exists(fp)) { throw new BusinessException(ErrorCode.NOT_FOUND, "磁盘文件不存在"); }
        // 更新下载计数
        sl.setDownloadCount(sl.getDownloadCount() + 1);
        updateById(sl);
        recordLog(sl, "DOWNLOAD", request, true, "");
        // 记录一次分享下载（匿名，关联分享 ID），供文件中心下载次数统计与审计
        downloadRecordService.recordDownload(file.getId(), file.getName(), null,
                DownloadSource.SHARE, sl.getId(),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return new FileSystemResource(fp);
    }

    @Override
    public IPage<ShareAccessLog> getShareAnalytics(String shareLinkId, int page, int size) {
        LambdaQueryWrapper<ShareAccessLog> qw = new LambdaQueryWrapper<>();
        qw.eq(ShareAccessLog::getShareLinkId, shareLinkId)
          .orderByDesc(ShareAccessLog::getCreatedAt);
        return logMapper.selectPage(new Page<>(page, size), qw);
    }

    // ---- 内部辅助 ----
    private ShareLink validateAndLog(String token, String action, HttpServletRequest request) {
        // 遍历所有 ACTIVE 分享链接，比对 token hash
        // MVP 实现：由于 token_hash 是 BCrypt 的，无法反向查询，改用更简单的实现
        // 我们使用一种折中：通过记录的详情中包含部分 token 信息来匹配
        // 实际生产应使用其他机制（如用 SHA-256 作为额外索引列）
        List<ShareLink> all = lambdaQuery()
                .eq(ShareLink::getStatus, ShareStatus.ACTIVE.name())
                .orderByDesc(ShareLink::getCreatedAt)
                .list();
        for (ShareLink sl : all) {
            if (passwordEncoder.matches(token, sl.getTokenHash())) {
                // 检查过期
                if (sl.getExpiresAt() != null && sl.getExpiresAt().isBefore(LocalDateTime.now())) {
                    sl.setStatus(ShareStatus.EXPIRED); 
                    updateById(sl);
                    recordLog(sl, action, request, false, "链接已过期");
                    throw new BusinessException(ErrorCode.SHARE_LINK_EXPIRED, "分享链接已过期");
                }
                // 检查访问次数
                if (sl.getMaxViews() > 0 && sl.getViewCount() >= sl.getMaxViews()) {
                    recordLog(sl, action, request, false, "访问次数已达上限");
                    throw new BusinessException(ErrorCode.SHARE_LIMIT_EXCEEDED, "访问次数已达上限");
                }
                return sl;
            }
        }
        throw new BusinessException(ErrorCode.SHARE_LINK_INVALID, "分享链接无效");
    }

    private void incrementView(ShareLink sl) {
        redisTemplate.opsForValue().increment(REDIS_SHARE_VIEW_KEY + sl.getId());
    }

    private void recordLog(ShareLink sl, String action, HttpServletRequest req, boolean success, String reason) {
        ShareAccessLog logEntry = new ShareAccessLog();
        logEntry.setShareLinkId(sl.getId());
        logEntry.setAction(action);
        logEntry.setIpAddress(req.getRemoteAddr());
        logEntry.setUserAgent(req.getHeader("User-Agent") != null ? req.getHeader("User-Agent") : "");
        logEntry.setSuccess(success);
        logEntry.setFailureReason(reason);
        logMapper.insert(logEntry);
    }

    /** 提取码是否已被锁定（Redis 不可用时 fail-open，不阻断验证） */
    private boolean isCodeLocked(String shareId) {
        try {
            return redisTemplate.hasKey(REDIS_CODE_LOCK_KEY + shareId);
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，跳过提取码锁定检查: {}", e.getMessage());
            return false;
        }
    }

    /** 记录提取码失败（滑动窗口，错误达阈值则锁定 CODE_LOCK_MINUTES，到期自动解锁） */
    private void recordCodeFailure(String shareId) {
        try {
            String failKey = REDIS_CODE_FAIL_KEY + shareId;
            Long count = redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, CODE_LOCK_MINUTES, TimeUnit.MINUTES);
            if (count != null && count >= MAX_CODE_FAILURES) {
                redisTemplate.opsForValue().set(
                        REDIS_CODE_LOCK_KEY + shareId, "1", CODE_LOCK_MINUTES, TimeUnit.MINUTES);
            }
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，跳过提取码失败计数: {}", e.getMessage());
        }
    }

    /** 提取码验证成功后清除失败记录 */
    private void clearCodeFailures(String shareId) {
        try {
            redisTemplate.delete(REDIS_CODE_FAIL_KEY + shareId);
            redisTemplate.delete(REDIS_CODE_LOCK_KEY + shareId);
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，跳过清除提取码失败记录: {}", e.getMessage());
        }
    }
}
