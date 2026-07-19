package com.baiflow.auth.controller;

import com.baiflow.auth.dto.request.LoginRequest;
import com.baiflow.auth.dto.response.LoginResponse;
import com.baiflow.auth.service.AuthService;
import com.baiflow.common.entity.ApiResponse;
import com.baiflow.user.dto.response.UserInfo;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口控制器 — 处理登录、登出和当前用户信息查询。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    /**
     * 用户登录 — 验证凭据，返回 JWT 令牌和用户信息。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 用户登出 — JWT 无状态，登出由客户端丢弃令牌完成。
     */
    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout() {
        return ApiResponse.success(Map.of("result", "已登出"));
    }

    /**
     * 获取当前已登录用户的信息。
     */
    @GetMapping("/me")
    public ApiResponse<UserInfo> me(Authentication authentication) {
        return ApiResponse.success(authService.me(authentication.getPrincipal().toString()));
    }
}
