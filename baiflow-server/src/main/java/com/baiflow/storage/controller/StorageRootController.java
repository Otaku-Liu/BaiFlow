package com.baiflow.storage.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.storage.dto.request.CreateStorageRootRequest;
import com.baiflow.storage.dto.request.UpdateStorageRootRequest;
import com.baiflow.storage.dto.response.NasCheckResult;
import com.baiflow.storage.dto.response.StorageRootInfo;
import com.baiflow.storage.service.StorageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 存储根目录管理接口控制器 — 仅限 ADMIN 角色访问。
 */
@RestController
@RequestMapping("/api/storage-roots")
public class StorageRootController {

    private final StorageService storageService;

    public StorageRootController(StorageService storageService) { this.storageService = storageService; }

    /**
     * 分页列出所有存储根目录。
     */
    @GetMapping
    public ApiResponse<IPage<StorageRootInfo>> list(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(storageService.listRoots(page, size));
    }

    /**
     * 列出所有 ACTIVE 状态的存储根目录（供文件列表页面的根目录选择器使用）。
     */
    @GetMapping("/active")
    public ApiResponse<List<StorageRootInfo>> active() {
        return ApiResponse.success(storageService.listActiveRoots());
    }

    /**
     * 创建新的存储根目录。
     */
    @PostMapping
    public ApiResponse<StorageRootInfo> create(@Valid @RequestBody CreateStorageRootRequest req) {
        return ApiResponse.success(storageService.createRoot(req));
    }

    /**
     * 更新存储根目录的名称、状态或只读标志。
     */
    @PatchMapping("/{id}")
    public ApiResponse<StorageRootInfo> update(@PathVariable String id,
                                                @RequestBody UpdateStorageRootRequest req) {
        return ApiResponse.success(storageService.updateRoot(id, req));
    }

    /**
     * 手动检测指定存储根目录的 NAS 连通性。
     * 管理员可调用此端点主动检查 NAS 挂载路径是否可用。
     */
    @PostMapping("/{id}/check")
    public ApiResponse<NasCheckResult> checkNas(@PathVariable String id) {
        return ApiResponse.success(storageService.checkNasAccessibility(id));
    }
}
