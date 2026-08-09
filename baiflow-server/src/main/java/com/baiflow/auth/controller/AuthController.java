package com.baiflow.auth.controller;

import com.baiflow.auth.dto.request.LoginRequest;
import com.baiflow.auth.dto.response.AuthSessionInfo;
import com.baiflow.auth.dto.response.LoginResponse;
import com.baiflow.auth.dto.response.UserDeviceInfo;
import com.baiflow.auth.security.AuthTokens;
import com.baiflow.auth.service.AuthService;
import com.baiflow.common.entity.ApiResponse;
import com.baiflow.user.dto.response.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 认证接口控制器 — 处理登录、登出和当前用户信息查询。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 用户登录 — 验证凭据，建登录会话，返回会话 token 和用户信息。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 用户登出 — 吊销当前请求 token 对应的登录会话（立即生效）。
     */
    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(HttpServletRequest request) {
        authService.logout(AuthTokens.extract(request));
        return ApiResponse.success(Map.of("result", "已登出"));
    }

    /**
     * 当前用户的登录设备列表（含当前会话标记），供 Web 端管理/强制下线。
     */
    @GetMapping("/sessions")
    public ApiResponse<List<AuthSessionInfo>> sessions(Authentication auth, HttpServletRequest request) {
        return ApiResponse.success(
                authService.listSessions(auth.getPrincipal().toString(), AuthTokens.extract(request)));
    }

    /**
     * 当前用户的登录设备列表（含历史与在线状态），供 Web 端展示「登录过的设备 + 是否在线」。
     */
    @GetMapping("/devices")
    public ApiResponse<List<UserDeviceInfo>> devices(Authentication auth, HttpServletRequest request) {
        return ApiResponse.success(
                authService.listDevices(auth.getPrincipal().toString(), AuthTokens.extract(request)));
    }

    /**
     * 强制下线某登录设备（本人任意会话；管理员可下线任意用户的会话）。
     */
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Map<String, Object>> revokeSession(@PathVariable String id,
                                                          Authentication auth) {
        authService.revokeSession(auth.getPrincipal().toString(), isAdmin(auth), id);
        return ApiResponse.success(Map.of("result", "已强制下线"));
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(ga.getAuthority())) return true;
        }
        return false;
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
        authService.changePassword(authentication.getPrincipal().toString(),
                body.get("oldPassword"), body.get("newPassword"));
        return ApiResponse.success(Map.of("result", "密码已修改"));
    }
}
