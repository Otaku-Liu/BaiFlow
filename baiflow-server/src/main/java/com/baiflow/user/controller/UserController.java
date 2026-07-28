package com.baiflow.user.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.user.dto.request.CreateUserRequest;
import com.baiflow.user.dto.request.ResetPasswordRequest;
import com.baiflow.user.dto.request.UpdateUserRequest;
import com.baiflow.user.dto.response.UserInfo;
import com.baiflow.user.service.UserService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 用户管理接口控制器 — 仅限 ADMIN 角色访问。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 分页查询用户列表，支持按角色、状态和展示名筛选。
     */
    @GetMapping
    public ApiResponse<IPage<UserInfo>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String role,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String displayName) {
        return ApiResponse.success(userService.listUsers(page, size, role, status, displayName));
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

    /**
     * 批量删除用户（ID 列表通过 ids 查询参数传入，逗号分隔）— 事务性操作。
     * <p>
     * 不允许删除当前登录用户。删除用户时其拥有的文件从磁盘和数据库硬删除，
     * 下载记录和分享记录保留（denormalized owner 字段留存快照）。
     */
    @DeleteMapping
    public ApiResponse<Map<String, Object>> batchDelete(@RequestParam String ids, Authentication auth) {
        List<String> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        String currentUserId = auth.getPrincipal().toString();
        userService.batchDelete(idList, currentUserId);
        return ApiResponse.success(Map.of("result", "已删除 " + idList.size() + " 个用户"));
    }
}
