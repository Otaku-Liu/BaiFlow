package com.baiflow.audit.controller;

import com.baiflow.audit.dto.response.LoginLogVO;
import com.baiflow.audit.service.BfAuditLogService;
import com.baiflow.common.entity.ApiResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志管理接口 — 仅限 ADMIN 角色访问。
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class BfAuditLogController {

    @Autowired
    private BfAuditLogService auditService;

    /**
     * 分页查询登录日志，支持用户名模糊搜索、登录结果和日期范围筛选。
     */
    @GetMapping("/login")
    public ApiResponse<Page<LoginLogVO>> loginLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResponse.success(auditService.queryLoginLogs(page, size, username, status, startDate, endDate));
    }
}
