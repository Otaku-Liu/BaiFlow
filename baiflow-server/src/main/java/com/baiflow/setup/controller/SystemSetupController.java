package com.baiflow.setup.controller;

import com.baiflow.auth.dto.response.LoginResponse;
import com.baiflow.common.entity.ApiResponse;
import com.baiflow.setup.dto.request.SystemSetupRequest;
import com.baiflow.setup.dto.response.SystemSetupStatus;
import com.baiflow.setup.service.SystemSetupService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统初始化接口控制器 — 首次部署向导，公开访问（无需登录）。
 * <p>
 * 两步：先查 {@code /status} 判断是否需要初始化，再带令牌调 {@code /init} 创建第一个管理员。
 * 初始化完成后再调 {@code /init} 恒返回 SETUP_ALREADY_INITIALIZED。
 */
@RestController
@RequestMapping("/api/setup")
public class SystemSetupController {

    @Autowired
    private SystemSetupService systemSetupService;

    /**
     * 查询系统是否已完成首次初始化（供 Web 端路由守卫判断是否强制跳转向导）。
     */
    @GetMapping("/status")
    public ApiResponse<SystemSetupStatus> status() {
        return ApiResponse.success(new SystemSetupStatus(systemSetupService.isInitialized()));
    }

    /**
     * 首次初始化：创建第一个管理员并直接登录。
     */
    @PostMapping("/init")
    public ApiResponse<LoginResponse> init(@Valid @RequestBody SystemSetupRequest request) {
        return ApiResponse.success(systemSetupService.initialize(request));
    }
}
