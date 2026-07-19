package com.baiflow.storage.service.impl;

import com.baiflow.common.entity.ApiResponse.Code;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.storage.dto.request.CreateStorageRootRequest;
import com.baiflow.storage.dto.request.UpdateStorageRootRequest;
import com.baiflow.storage.dto.response.NasCheckResult;
import com.baiflow.storage.dto.response.StorageRootInfo;
import com.baiflow.storage.entity.StorageRoot;
import com.baiflow.storage.enums.StorageRootStatus;
import com.baiflow.storage.enums.StorageRootType;
import com.baiflow.storage.mapper.StorageRootMapper;
import com.baiflow.storage.service.StorageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 存储根目录管理服务实现。
 */
@Service
public class StorageServiceImpl implements StorageService {

    private final StorageRootMapper mapper;

    public StorageServiceImpl(StorageRootMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public StorageRootInfo createRoot(CreateStorageRootRequest req) {
        StorageRootType type = StorageRootType.valueOf(req.type());
        StorageRoot r = new StorageRoot();
        r.setName(req.name());
        r.setType(type);
        r.setRootPath(req.rootPath());
        r.setReadonly(req.readonly());

        // 根据类型处理磁盘路径
        if (type == StorageRootType.NAS_MOUNT) {
            // NAS 挂载路径：仅检查存在性，不创建目录
            Path nasPath = Path.of(req.rootPath()).toAbsolutePath().normalize();
            if (!Files.exists(nasPath)) {
                // NAS 路径不存在时仍允许创建记录，但标记为 OFFLINE
                r.setStatus(StorageRootStatus.OFFLINE);
            } else {
                r.setStatus(StorageRootStatus.ACTIVE);
            }
        } else {
            // LOCAL 类型：创建目录，标记为 ACTIVE
            r.setStatus(StorageRootStatus.ACTIVE);
            try {
                Files.createDirectories(resolveRootPath(r));
            } catch (Exception e) {
                throw new BusinessException(Code.FILE_OPERATION_FAILED,
                        "无法创建存储根目录：" + resolveRootPath(r));
            }
        }

        mapper.insert(r);
        return StorageRootInfo.from(r);
    }

    @Override
    public IPage<StorageRootInfo> listRoots(int page, int size) {
        List<StorageRoot> all = mapper.selectAllOrdered(null);
        int total = all.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<StorageRootInfo> recs = (from < total ? all.subList(from, to) : List.<StorageRoot>of())
                .stream().map(StorageRootInfo::from).toList();
        IPage<StorageRootInfo> r = new Page<>(page, size, total);
        r.setRecords(recs);
        return r;
    }

    @Override
    public List<StorageRootInfo> listActiveRoots() {
        // 仅返回 ACTIVE 状态的根目录，供文件列表页面的根目录选择器使用
        return mapper.selectAllOrdered(StorageRootStatus.ACTIVE.name())
                .stream().map(StorageRootInfo::from).toList();
    }

    @Override
    public StorageRootInfo updateRoot(String id, UpdateStorageRootRequest req) {
        StorageRoot r = getByIdOrThrow(id);
        if (req.name() != null) { r.setName(req.name()); }
        if (req.status() != null) { r.setStatus(StorageRootStatus.valueOf(req.status())); }
        if (req.readonly() != null) { r.setReadonly(req.readonly()); }
        mapper.updateById(r);
        return StorageRootInfo.from(r);
    }

    @Override
    public StorageRoot getByIdOrThrow(String id) {
        StorageRoot r = mapper.selectById(id);
        if (r == null) { throw new BusinessException(Code.NOT_FOUND, "存储根目录不存在"); }
        if (r.getStatus() == StorageRootStatus.DISABLED) {
            throw new BusinessException(Code.STORAGE_ROOT_OFFLINE, "存储根目录已禁用：" + r.getName());
        }
        // OFFLINE 状态仅记录 NAS 暂不可用，仍允许返回实体以支持元数据浏览
        return r;
    }

    @Override
    public Path resolveRootPath(StorageRoot root) {
        // 规范化为绝对路径——作为所有路径穿越防护的参考锚点
        return Path.of(root.getRootPath()).toAbsolutePath().normalize();
    }

    @Override
    public void verifyPathInRoot(StorageRoot root, Path resolved) {
        // 核心路径穿越防护：
        // 每个被解析的路径必须在存储根目录的绝对路径范围内，否则拒绝访问
        if (!resolved.startsWith(resolveRootPath(root))) {
            throw new BusinessException(Code.FILE_OPERATION_FAILED, "检测到路径穿越攻击");
        }
    }

    @Override
    public NasCheckResult checkNasAccessibility(String rootId) {
        StorageRoot root = mapper.selectById(rootId);
        if (root == null) { throw new BusinessException(Code.NOT_FOUND, "存储根目录不存在"); }

        Path path = resolveRootPath(root);
        boolean accessible = Files.exists(path) && Files.isDirectory(path);

        // 自动更新状态
        StorageRootStatus newStatus;
        String message;
        if (accessible) {
            newStatus = StorageRootStatus.ACTIVE;
            message = "路径可访问：" + path;
        } else {
            newStatus = StorageRootStatus.OFFLINE;
            message = "路径不可访问，请检查 NAS 是否已挂载：" + path;
        }

        if (root.getStatus() != StorageRootStatus.DISABLED
                && root.getStatus() != newStatus) {
            root.setStatus(newStatus);
            mapper.updateById(root);
        }

        return new NasCheckResult(root.getId(), root.getName(), path.toString(),
                accessible, message, newStatus.name());
    }

    @Override
    public int checkAllNasRoots() {
        // 查询所有 NAS_MOUNT 类型的存储根目录
        List<StorageRoot> nasRoots = mapper.selectByType(StorageRootType.NAS_MOUNT.name());
        int updated = 0;
        for (StorageRoot root : nasRoots) {
            if (root.getStatus() == StorageRootStatus.DISABLED) { continue; }

            Path path = resolveRootPath(root);
            boolean accessible = Files.exists(path) && Files.isDirectory(path);

            StorageRootStatus expected = accessible ? StorageRootStatus.ACTIVE : StorageRootStatus.OFFLINE;
            if (root.getStatus() != expected) {
                root.setStatus(expected);
                mapper.updateById(root);
                updated++;
            }
        }
        return updated;
    }
}
