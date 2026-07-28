package com.baiflow.auth.controller;

import com.baiflow.auth.dto.request.LoginRequest;
import com.baiflow.auth.dto.response.LoginResponse;
import com.baiflow.auth.service.AuthService;
import com.baiflow.common.entity.ApiResponse;
import com.baiflow.user.dto.response.UserInfo;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 更新当前用户的展示名称。
     */
    @PatchMapping("/profile")
    public ApiResponse<UserInfo> updateProfile(@RequestBody Map<String, String> body,
                                               Authentication authentication) {
        return ApiResponse.success(authService.updateProfile(
                authentication.getPrincipal().toString(),
                body.get("displayName")));
    }

    /**
     * 上传/更新当前用户的头像。文件大小 ≤1MB，仅支持 jpg/jpeg/png/gif/webp。
     */
    @PostMapping("/avatar")
    public ApiResponse<UserInfo> uploadAvatar(@RequestParam("file") MultipartFile file,
                                              Authentication authentication) {
        return ApiResponse.success(authService.uploadAvatar(
                authentication.getPrincipal().toString(), file));
    }

    /**
     * 修改当前用户的密码，需提供旧密码验证身份。
     */
    @PostMapping("/change-password")
    public ApiResponse<Map<String, Object>> changePassword(@RequestBody Map<String, String> body,
                                                           Authentication authentication) {
        authService.changePassword(
                authentication.getPrincipal().toString(),
                body.get("oldPassword"),
                body.get("newPassword"));
        return ApiResponse.success(Map.of("result", "密码已修改"));
    }
}
