package com.baiflow.health.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.health.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查接口控制器 — 公开访问，无需认证。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) { this.healthService = healthService; }

    /**
     * 返回服务健康状态，包含数据库连通性检测。
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(healthService.health());
    }
}
