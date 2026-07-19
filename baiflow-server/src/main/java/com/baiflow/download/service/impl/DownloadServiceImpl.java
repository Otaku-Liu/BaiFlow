package com.baiflow.download.service.impl;

import com.baiflow.common.entity.ApiResponse.Code;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.download.dto.request.CreateDownloadRequest;
import com.baiflow.download.dto.response.DownloadTaskInfo;
import com.baiflow.download.entity.DownloadTask;
import com.baiflow.download.enums.DownloadTaskStatus;
import com.baiflow.download.mapper.DownloadTaskMapper;
import com.baiflow.download.service.Aria2Client;
import com.baiflow.download.service.DownloadService;
import com.baiflow.file.entity.FileItem;
import com.baiflow.file.enums.FileItemStatus;
import com.baiflow.file.enums.ItemType;
import com.baiflow.file.enums.PrivacyMode;
import com.baiflow.file.mapper.FileItemMapper;
import com.baiflow.storage.entity.StorageRoot;
import com.baiflow.storage.service.StorageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 下载任务管理服务实现。
 * <p>
 * 下载任务生命周期：
 * <ol>
 *   <li>用户创建下载 → WAITING（已提交给 aria2）</li>
 *   <li>aria2 开始下载 → RUNNING（由同步任务更新）</li>
 *   <li>用户暂停 → PAUSED</li>
 *   <li>用户恢复 → RUNNING</li>
 *   <li>下载完成 → COMPLETED（自动创建 file_item 记录）</li>
 *   <li>下载失败 → FAILED（记录错误信息）</li>
 *   <li>用户删除 → DELETED</li>
 * </ol>
 */
@Service
public class DownloadServiceImpl implements DownloadService {

    private static final Logger log = LoggerFactory.getLogger(DownloadServiceImpl.class);

    private final DownloadTaskMapper taskMapper;
    private final Aria2Client aria2Client;
    private final StorageService storageService;
    private final FileItemMapper fileItemMapper;

    public DownloadServiceImpl(DownloadTaskMapper taskMapper, Aria2Client aria2Client,
                                StorageService storageService, FileItemMapper fileItemMapper) {
        this.taskMapper = taskMapper;
        this.aria2Client = aria2Client;
        this.storageService = storageService;
        this.fileItemMapper = fileItemMapper;
    }

    @Override
    @Transactional
    public DownloadTaskInfo createDownload(CreateDownloadRequest req, String userId) {
        // 校验目标存储根目录
        StorageRoot root = storageService.getByIdOrThrow(req.targetStorageRootId());
        Path rootPath = storageService.resolveRootPath(root);

        // 构建目标路径
        String subPath = (req.targetRelativePath() != null && !req.targetRelativePath().isBlank())
                ? req.targetRelativePath() : "";
        Path targetDir = rootPath.resolve(subPath).normalize();
        storageService.verifyPathInRoot(root, targetDir);

        // 从 URL 推断文件名
        String fileName = extractFileName(req.sourceUrl());

        // 确保目标目录存在
        try { Files.createDirectories(targetDir); } catch (Exception e) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "无法创建下载目标目录：" + e.getMessage());
        }

        // 通过 aria2 RPC 提交下载任务
        String gid = aria2Client.addUri(req.sourceUrl(),
                targetDir.toAbsolutePath().toString(), fileName);

        // 持久化任务记录
        DownloadTask task = new DownloadTask();
        task.setCreatedBy(userId);
        task.setSourceUrl(req.sourceUrl());
        task.setAria2Gid(gid);
        task.setTargetStorageRootId(req.targetStorageRootId());
        task.setTargetRelativePath(subPath.isEmpty() ? fileName : subPath + "/" + fileName);
        task.setFileName(fileName);
        task.setStatus(DownloadTaskStatus.WAITING);
        task.setProgress(0);
        task.setTotalBytes(0L);
        task.setCompletedBytes(0L);
        task.setSpeedBytesPerSecond(0L);
        taskMapper.insert(task);

        log.info("下载任务已创建: id={}, url={}, gid={}, user={}", task.getId(), req.sourceUrl(), gid, userId);
        return DownloadTaskInfo.from(task);
    }

    @Override
    public IPage<DownloadTaskInfo> listDownloads(String userId, boolean isAdmin, String status,
                                                  int page, int size) {
        // 非管理员只能查看自己的任务
        String queryUserId = isAdmin ? null : userId;
        int offset = (page - 1) * size;

        List<DownloadTask> tasks;
        int total;

        if (isAdmin) {
            // 管理员查看所有：先取所有活跃的，再内存筛选
            List<DownloadTask> all = taskMapper.selectByUser(null, null, 0, 10000);
            if (status != null && !status.isBlank()) {
                all = all.stream().filter(t -> t.getStatus().name().equals(status)).toList();
            }
            // 按创建时间倒序
            all = all.stream().sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())).toList();
            total = all.size();
            int from = Math.min(offset, total);
            int to = Math.min(from + size, total);
            tasks = from < total ? all.subList(from, to) : List.of();
        } else {
            total = taskMapper.countByUser(userId, status);
            tasks = taskMapper.selectByUser(userId, status, offset, size);
        }

        IPage<DownloadTaskInfo> result = new Page<>(page, size, total);
        result.setRecords(tasks.stream().map(DownloadTaskInfo::from).toList());
        return result;
    }

    @Override
    public DownloadTaskInfo getById(String taskId, String userId, boolean isAdmin) {
        DownloadTask task = taskMapper.selectById(taskId);
        if (task == null || task.getStatus() == DownloadTaskStatus.DELETED) {
            throw new BusinessException(Code.NOT_FOUND, "下载任务不存在");
        }
        if (!isAdmin && !task.getCreatedBy().equals(userId)) {
            throw new BusinessException(Code.FORBIDDEN, "无权查看此下载任务");
        }
        return DownloadTaskInfo.from(task);
    }

    @Override
    @Transactional
    public DownloadTaskInfo pauseDownload(String taskId, String userId, boolean isAdmin) {
        DownloadTask task = checkOwnership(taskId, userId, isAdmin);

        if (task.getStatus() != DownloadTaskStatus.WAITING
                && task.getStatus() != DownloadTaskStatus.RUNNING) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "当前状态不允许暂停：" + task.getStatus());
        }

        // 通过 aria2 RPC 暂停
        if (task.getStatus() == DownloadTaskStatus.RUNNING) {
            aria2Client.pause(task.getAria2Gid());
        }

        // 更新本地状态
        task.setStatus(DownloadTaskStatus.PAUSED);
        taskMapper.updateById(task);

        return DownloadTaskInfo.from(task);
    }

    @Override
    @Transactional
    public DownloadTaskInfo resumeDownload(String taskId, String userId, boolean isAdmin) {
        DownloadTask task = checkOwnership(taskId, userId, isAdmin);

        if (task.getStatus() != DownloadTaskStatus.PAUSED) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "当前状态不允许恢复：" + task.getStatus());
        }

        // 通过 aria2 RPC 恢复
        aria2Client.unpause(task.getAria2Gid());

        // 更新本地状态
        task.setStatus(DownloadTaskStatus.RUNNING);
        taskMapper.updateById(task);

        return DownloadTaskInfo.from(task);
    }

    @Override
    @Transactional
    public void deleteDownload(String taskId, String userId, boolean isAdmin) {
        DownloadTask task = checkOwnership(taskId, userId, isAdmin);

        // 已完成/失败的任务直接标记删除，不调用 aria2
        if (task.getStatus() != DownloadTaskStatus.COMPLETED
                && task.getStatus() != DownloadTaskStatus.FAILED
                && task.getStatus() != DownloadTaskStatus.DELETED) {
            try {
                aria2Client.remove(task.getAria2Gid());
            } catch (Exception e) {
                log.warn("aria2 删除任务失败（继续标记本地记录）: gid={}, error={}",
                        task.getAria2Gid(), e.getMessage());
            }
        }

        task.setStatus(DownloadTaskStatus.DELETED);
        taskMapper.updateById(task);
    }

    @Override
    @Transactional
    public int syncActiveTasks() {
        // 查询所有活跃任务
        List<DownloadTask> activeTasks = taskMapper.selectActive();
        if (activeTasks.isEmpty()) { return 0; }

        int updatedCount = 0;
        for (DownloadTask task : activeTasks) {
            try {
                updatedCount += syncSingleTask(task);
            } catch (Exception e) {
                log.warn("同步下载任务失败: taskId={}, gid={}, error={}",
                        task.getId(), task.getAria2Gid(), e.getMessage());
            }
        }
        return updatedCount;
    }

    // -------------------------------------------------------
    // 内部方法
    // -------------------------------------------------------

    /**
     * 同步单个下载任务的状态。
     */
    private int syncSingleTask(DownloadTask task) {
        try {
            Map<String, Object> status = aria2Client.tellStatus(task.getAria2Gid());
            return applyAria2Status(task, status);
        } catch (BusinessException e) {
            // aria2 中任务不存在：标记为失败
            if (task.getStatus() != DownloadTaskStatus.FAILED) {
                task.setStatus(DownloadTaskStatus.FAILED);
                task.setErrorMessage("aria2 返回错误：" + e.getMessage());
                taskMapper.updateById(task);
                return 1;
            }
            return 0;
        }
    }

    /**
     * 将 aria2 返回的状态应用到本地任务记录。
     */
    private int applyAria2Status(DownloadTask task, Map<String, Object> ariaStatus) {
        boolean changed = false;

        // 映射 aria2 状态到本地状态
        String ariaState = String.valueOf(ariaStatus.getOrDefault("status", "unknown"));
        DownloadTaskStatus localStatus = mapAria2Status(ariaState);

        if (localStatus != task.getStatus()) {
            task.setStatus(localStatus);
            if (localStatus == DownloadTaskStatus.COMPLETED) {
                task.setCompletedAt(LocalDateTime.now());
                // 下载完成后自动创建文件记录
                createFileItemForCompletedTask(task, ariaStatus);
            }
            changed = true;
        }

        // 更新进度和速度
        long total = toLong(ariaStatus.get("totalLength"));
        long completed = toLong(ariaStatus.get("completedLength"));
        long speed = toLong(ariaStatus.get("downloadSpeed"));

        if (total > 0) {
            int progress = (int) ((completed * 100) / total);
            if (!Integer.valueOf(progress).equals(task.getProgress())) {
                task.setProgress(progress);
                changed = true;
            }
        }
        if (total != (task.getTotalBytes() != null ? task.getTotalBytes() : 0L)) {
            task.setTotalBytes(total);
            changed = true;
        }
        if (completed != (task.getCompletedBytes() != null ? task.getCompletedBytes() : 0L)) {
            task.setCompletedBytes(completed);
            changed = true;
        }
        if (speed != (task.getSpeedBytesPerSecond() != null ? task.getSpeedBytesPerSecond() : 0L)) {
            task.setSpeedBytesPerSecond(speed);
            changed = true;
        }

        // 记录错误信息
        String errorMsg = String.valueOf(ariaStatus.getOrDefault("errorMessage", ""));
        if (!errorMsg.isEmpty() && !"null".equals(errorMsg)
                && !errorMsg.equals(task.getErrorMessage() != null ? task.getErrorMessage() : "")) {
            task.setErrorMessage(errorMsg);
            changed = true;
        }

        if (changed) { taskMapper.updateById(task); }
        return changed ? 1 : 0;
    }

    /**
     * 下载完成后在 file_item 表中创建文件记录。
     */
    private void createFileItemForCompletedTask(DownloadTask task, Map<String, Object> ariaStatus) {
        // 检查是否已存在文件记录（幂等性）
        if (fileItemMapper.selectByPath(task.getTargetStorageRootId(), task.getTargetRelativePath()) != null) {
            return;
        }

        // 从 aria2 状态中获取文件信息
        long fileSize = toLong(ariaStatus.get("totalLength"));

        FileItem item = new FileItem();
        item.setStorageRootId(task.getTargetStorageRootId());

        // 解析父目录路径
        String relPath = task.getTargetRelativePath();
        if (relPath == null || relPath.isBlank()) {
            relPath = task.getFileName();
        }
        // 父目录 ID 先设置为 null（根目录），后续可根据实际路径查找
        item.setParentId(null);
        item.setOwnerUserId(task.getCreatedBy());
        item.setName(task.getFileName() != null ? task.getFileName() : extractFileName(task.getSourceUrl()));
        item.setRelativePath(relPath);
        item.setItemType(ItemType.FILE);
        item.setSizeBytes(fileSize);
        item.setMimeType("application/octet-stream");
        item.setPrivacyMode(PrivacyMode.NORMAL);
        item.setStatus(FileItemStatus.ACTIVE);

        fileItemMapper.insert(item);
        log.info("下载完成，已创建文件记录: fileName={}, size={}", item.getName(), fileSize);
    }

    /**
     * 将 aria2 状态字符串映射为本地状态枚举。
     */
    private DownloadTaskStatus mapAria2Status(String ariaState) {
        return switch (ariaState) {
            case "waiting" -> DownloadTaskStatus.WAITING;
            case "active" -> DownloadTaskStatus.RUNNING;
            case "paused" -> DownloadTaskStatus.PAUSED;
            case "complete" -> DownloadTaskStatus.COMPLETED;
            case "error", "removed" -> DownloadTaskStatus.FAILED;
            default -> DownloadTaskStatus.WAITING;
        };
    }

    /**
     * 校验任务归属：非管理员只能操作自己的任务。
     */
    private DownloadTask checkOwnership(String taskId, String userId, boolean isAdmin) {
        DownloadTask task = taskMapper.selectById(taskId);
        if (task == null || task.getStatus() == DownloadTaskStatus.DELETED) {
            throw new BusinessException(Code.NOT_FOUND, "下载任务不存在");
        }
        if (!isAdmin && !task.getCreatedBy().equals(userId)) {
            throw new BusinessException(Code.FORBIDDEN, "无权操作此下载任务");
        }
        return task;
    }

    /**
     * 从 URL 中提取文件名。若无法提取则使用时间戳命名。
     */
    private String extractFileName(String url) {
        if (url == null || url.isBlank()) { return "download_" + System.currentTimeMillis(); }
        try {
            String path = url;
            // 去掉 query string
            int qIdx = path.indexOf('?');
            if (qIdx >= 0) { path = path.substring(0, qIdx); }
            // 去掉 fragment
            int fIdx = path.indexOf('#');
            if (fIdx >= 0) { path = path.substring(0, fIdx); }
            // 取最后一段作为文件名
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (name.isBlank()) { name = "download_" + System.currentTimeMillis(); }
            return name;
        } catch (Exception e) {
            return "download_" + System.currentTimeMillis();
        }
    }

    private long toLong(Object value) {
        if (value == null) { return 0L; }
        if (value instanceof Number n) { return n.longValue(); }
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception e) { return 0L; }
    }
}
