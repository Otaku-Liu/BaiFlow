package com.baiflow.transfer.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.transfer.dto.response.TransferTaskInfo;
import com.baiflow.transfer.service.TransferService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 传输任务接口控制器 — 统一管理上传、下载、设备流转任务。
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) { this.transferService = transferService; }

    /**
     * 分页查询当前用户的传输任务，支持按任务类型和状态筛选。
     */
    @GetMapping
    public ApiResponse<IPage<TransferTaskInfo>> list(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ApiResponse.success(
                transferService.listTasks(auth.getPrincipal().toString(), taskType, status, page, size));
    }

    /**
     * 查询单个传输任务详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<TransferTaskInfo> get(@PathVariable String id, Authentication auth) {
        return ApiResponse.success(transferService.getTask(id, auth.getPrincipal().toString()));
    }
}
