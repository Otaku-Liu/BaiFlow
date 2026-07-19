package com.baiflow.user.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.user.dto.request.CreateUserRequest;
import com.baiflow.user.dto.request.ResetPasswordRequest;
import com.baiflow.user.dto.request.UpdateUserRequest;
import com.baiflow.user.dto.response.UserInfo;
import com.baiflow.user.service.UserService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理接口控制器 — 仅限 ADMIN 角色访问。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) { this.userService = userService; }

    /**
     * 分页查询用户列表，支持按角色和状态筛选。
     */
    @GetMapping
    public ApiResponse<IPage<UserInfo>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String role,
                                              @RequestParam(required = false) String status) {
        return ApiResponse.success(userService.listUsers(page, size, role, status));
    }

    /**
     * 根据 ID 查询单个用户。
     */
    @GetMapping("/{id}")
    public ApiResponse<UserInfo> get(@PathVariable String id) {
        return ApiResponse.success(userService.getUser(id));
    }

    /**
     * 创建新用户。
     */
    @PostMapping
    public ApiResponse<UserInfo> create(@Valid @RequestBody CreateUserRequest req) {
        return ApiResponse.success(userService.createUser(req));
    }

    /**
     * 更新用户信息（部分更新：仅更新传入的非空字段）。
     */
    @PatchMapping("/{id}")
    public ApiResponse<UserInfo> update(@PathVariable String id, @RequestBody UpdateUserRequest req) {
        return ApiResponse.success(userService.updateUser(id, req));
    }

    /**
     * 重置用户密码。
     */
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable String id,
                                                          @Valid @RequestBody ResetPasswordRequest req) {
        userService.resetPassword(id, req);
        return ApiResponse.success(Map.of("result", "密码已重置"));
    }
}
