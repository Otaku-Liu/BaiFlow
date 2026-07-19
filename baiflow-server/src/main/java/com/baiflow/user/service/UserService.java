package com.baiflow.user.service;

import com.baiflow.user.dto.request.CreateUserRequest;
import com.baiflow.user.dto.request.ResetPasswordRequest;
import com.baiflow.user.dto.request.UpdateUserRequest;
import com.baiflow.user.dto.response.UserInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 用户管理服务 — 仅限 ADMIN 角色使用。
 */
public interface UserService {

    /**
     * 创建新用户，指定角色和初始密码。密码在存储前进行 BCrypt 哈希处理。
     *
     * @param request 用户名、密码、显示名称和角色
     * @return 新创建的用户信息
     * @throws com.baiflow.common.exception.BusinessException USERNAME_EXISTS 用户名已存在
     */
    UserInfo createUser(CreateUserRequest request);

    /**
     * 分页列出所有用户，支持按角色和状态筛选。仅 ADMIN 可调用。
     *
     * @param page   页码（从 1 开始）
     * @param size   每页数量
     * @param role   可选的角色筛选（ADMIN / USER / GUEST）
     * @param status 可选的状态筛选（ACTIVE / DISABLED / LOCKED）
     * @return 分页用户列表
     */
    IPage<UserInfo> listUsers(int page, int size, String role, String status);

    /**
     * 根据 ID 查询单个用户。
     *
     * @param id 用户 ID
     * @return 用户信息
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND 用户不存在
     */
    UserInfo getUser(String id);

    /**
     * 更新用户的显示名称、角色或状态。仅更新传入的非空字段。
     *
     * @param id      目标用户 ID
     * @param request 需要更新的字段（均为可选）
     * @return 更新后的用户信息
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND 用户不存在
     */
    UserInfo updateUser(String id, UpdateUserRequest request);

    /**
     * 重置用户密码（BCrypt 哈希存储）。
     *
     * @param id      目标用户 ID
     * @param request 新密码（明文）
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND 用户不存在
     */
    void resetPassword(String id, ResetPasswordRequest request);
}
