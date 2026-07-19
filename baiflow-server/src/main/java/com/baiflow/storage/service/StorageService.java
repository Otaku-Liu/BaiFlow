package com.baiflow.storage.service;

import com.baiflow.storage.dto.request.CreateStorageRootRequest;
import com.baiflow.storage.dto.request.UpdateStorageRootRequest;
import com.baiflow.storage.dto.response.StorageRootInfo;
import com.baiflow.storage.dto.response.NasCheckResult;
import com.baiflow.storage.entity.StorageRoot;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.nio.file.Path;
import java.util.List;

/**
 * 存储根目录管理服务 — 仅限 ADMIN 角色使用。
 * <p>
 * 同时提供共享的路径解析和边界校验工具方法，供文件层调用以防止路径穿越攻击。
 */
public interface StorageService {

    /**
     * 创建新的存储根目录，并确保磁盘目录存在。
     * <p>
     * LOCAL 类型：自动创建目录（如不存在）。
     * NAS_MOUNT 类型：仅检查路径是否存在，不创建目录。
     *
     * @param req 名称、类型、根路径和只读标志
     * @return 创建后的存储根目录信息
     * @throws com.baiflow.common.exception.BusinessException FILE_OPERATION_FAILED 磁盘目录创建失败或 NAS 路径不存在
     */
    StorageRootInfo createRoot(CreateStorageRootRequest req);

    /**
     * 分页列出所有存储根目录（含离线、禁用状态）。
     */
    IPage<StorageRootInfo> listRoots(int page, int size);

    /**
     * 仅列出 ACTIVE 状态的存储根目录，供文件列表页面的根目录选择器使用。
     */
    List<StorageRootInfo> listActiveRoots();

    /**
     * 更新存储根目录的名称、状态或只读标志。
     */
    StorageRootInfo updateRoot(String id, UpdateStorageRootRequest req);

    /**
     * 根据 ID 获取存储根目录实体。
     * <p>
     * ACTIVE 和 OFFLINE 状态的根目录均可返回（NAS 离线时允许浏览历史元数据）。
     * DISABLED 状态抛出异常。
     *
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND           根目录不存在
     * @throws com.baiflow.common.exception.BusinessException STORAGE_ROOT_OFFLINE 根目录已禁用
     */
    StorageRoot getByIdOrThrow(String id);

    /**
     * 将配置的 {@code rootPath} 解析为绝对路径并规范化。这是所有文件操作必须遵循的参考锚点。
     */
    Path resolveRootPath(StorageRoot root);

    /**
     * 核心路径穿越防护：验证 {@code resolved} 路径是否在存储根目录的绝对路径范围内。
     * 如果不在范围内则抛出 FILE_OPERATION_FAILED 业务异常。
     */
    void verifyPathInRoot(StorageRoot root, Path resolved);

    /**
     * 检测指定存储根目录的 NAS 路径是否可访问。
     * <p>
     * 对于 LOCAL 类型始终返回 true。
     * 对于 NAS_MOUNT 类型，检查路径是否存在且可读。
     * 检测后自动更新存储根目录的 status（ACTIVE / OFFLINE）。
     *
     * @param rootId 存储根目录 ID
     * @return 检查结果，包含是否可访问和详细信息
     */
    NasCheckResult checkNasAccessibility(String rootId);

    /**
     * 定时检查所有 NAS_MOUNT 类型的存储根目录连通性，
     * 自动更新 status 字段（ACTIVE / OFFLINE）。
     *
     * @return 本次更新的存储根目录数量
     */
    int checkAllNasRoots();
}
