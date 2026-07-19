package com.baiflow.download.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.download.dto.request.CreateDownloadRequest;
import com.baiflow.download.dto.response.DownloadTaskInfo;
import com.baiflow.download.service.DownloadService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 下载任务管理接口控制器 — 创建、查询、暂停、恢复、删除下载任务。
 * <p>
 * 所有接口要求认证。非 ADMIN 用户只能操作自己创建的任务。
 */
@RestController
@RequestMapping("/api/downloads")
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    /**
     * 创建下载任务，提交给 aria2 引擎。
     */
    @PostMapping
    public ApiResponse<DownloadTaskInfo> create(@Valid @RequestBody CreateDownloadRequest req,
                                                 Authentication auth) {
        return ApiResponse.success(
                downloadService.createDownload(req, auth.getPrincipal().toString()));
    }

    /**
     * 分页查询下载任务列表，支持按状态筛选。
     * 非管理员仅返回自己的任务。
     */
    @GetMapping
    public ApiResponse<IPage<DownloadTaskInfo>> list(@RequestParam(required = false) String status,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      Authentication auth) {
        return ApiResponse.success(
                downloadService.listDownloads(auth.getPrincipal().toString(),
                        isAdmin(auth), status, page, size));
    }

    /**
     * 查询下载任务详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<DownloadTaskInfo> get(@PathVariable String id, Authentication auth) {
        return ApiResponse.success(
                downloadService.getById(id, auth.getPrincipal().toString(), isAdmin(auth)));
    }

    /**
     * 暂停正在运行的下载任务。
     */
    @PostMapping("/{id}/pause")
    public ApiResponse<DownloadTaskInfo> pause(@PathVariable String id, Authentication auth) {
        return ApiResponse.success(
                downloadService.pauseDownload(id, auth.getPrincipal().toString(), isAdmin(auth)));
    }

    /**
     * 恢复已暂停的下载任务。
     */
    @PostMapping("/{id}/resume")
    public ApiResponse<DownloadTaskInfo> resume(@PathVariable String id, Authentication auth) {
        return ApiResponse.success(
                downloadService.resumeDownload(id, auth.getPrincipal().toString(), isAdmin(auth)));
    }

    /**
     * 删除下载任务（逻辑删除，同时取消 aria2 中的任务）。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id, Authentication auth) {
        downloadService.deleteDownload(id, auth.getPrincipal().toString(), isAdmin(auth));
        return ApiResponse.success(Map.of("result", "已删除"));
    }

    private boolean isAdmin(Authentication a) {
        return a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
    }
}
