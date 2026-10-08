package com.baiflow.user.service;

import com.baiflow.user.dto.request.CreateUserRequest;
import com.baiflow.user.dto.request.ResetPasswordRequest;
import com.baiflow.user.dto.request.UpdateUserRequest;
import com.baiflow.user.dto.response.UserInfo;
import com.baiflow.user.entity.BfUser;
import com.baiflow.user.enums.UserStatus;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户服务 — 管理台用户 CRUD（仅 ADMIN）+ 当前用户自服务资料/头像。
 */
public interface BfUserService extends IService<BfUser> {

    /**
     * 创建新用户，指定角色和初始密码。密码在存储前进行 BCrypt 哈希处理。
     *
     * @param request 用户名、密码、显示名称和角色
     * @return 新创建的用户信息
     * @throws com.baiflow.common.exception.BusinessException USERNAME_EXISTS 用户名已存在
     */
    UserInfo createUser(CreateUserRequest request);

    /**
     * 分页列出所有用户，支持按角色、状态和展示名筛选。仅 ADMIN 可调用。
     *
     * @param page        页码（从 1 开始）
     * @param size        每页数量
     * @param role        可选的角色筛选（ADMIN / USER / GUEST）
     * @param status      可选的状态筛选（NORMAL / DISABLED）
     * @param displayName 可选的展示名模糊搜索
     * @return 分页用户列表
     */
    IPage<UserInfo> listUsers(int page, int size, String role, String status, String displayName);

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
     * <p>状态仅支持 {@code NORMAL} / {@code DISABLED}：只表达人工管理的禁用与恢复，
     * 登录失败锁定不落库（权威状态在 Redis 锁键，TTL 到期自动解除）。
     *
     * @param id      目标用户 ID
     * @param request 需要更新的字段（均为可选）
     * @return 更新后的用户信息
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND 用户不存在
     */
    UserInfo updateUser(String id, UpdateUserRequest request);

    /**
     * 批量设置用户状态（禁用/启用）。仅 ADMIN 可调用，目标仅限 USER 角色。
     * <p>目标状态为 {@code NORMAL}（启用）时，会同时清除其 Redis 登录锁定与失败计数——
     * 「启用」意味着给这个账号一个干净的登录状态。
     *
     * @param ids    目标用户 ID 列表
     * @param status 目标状态，仅支持 NORMAL / DISABLED
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND 目标用户不存在
     * @throws com.baiflow.common.exception.BusinessException FORBIDDEN 目标含 ADMIN 角色用户
     */
    void batchUpdateStatus(List<String> ids, UserStatus status);

    /**
     * 清除用户的登录锁定（Redis 锁键 + 失败计数），并记一条 info 级日志。
     * <p>登录成功、以及管理员把用户改回正常（启用）时调用，避免残留锁键在下次登录时仍拦截。
     * Redis 不可用时跳过（锁键本身会随 TTL 到期，不影响状态变更）。
     *
     * @param username 目标用户名
     */
    void clearLoginLock(String username);

    /**
     * 重置用户密码（BCrypt 哈希存储）。
     *
     * @param id      目标用户 ID
     * @param request 新密码（明文）
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND 用户不存在
     */
    void resetPassword(String id, ResetPasswordRequest request);

    /**
     * 批量删除用户（事务性 — 全部成功或全部回滚）。
     * <p>
     * 删除用户时：
     * <ul>
     *   <li>用户拥有的文件从磁盘和 file_item 表硬删除</li>
     *   <li>下载记录和分享记录保留（denormalized owner 字段已留存快照）</li>
     *   <li>不允许删除当前登录用户</li>
     *   <li>不允许删除系统内置管理员账号（admin）</li>
     * </ul>
     *
     * @param ids           要删除的用户 ID 列表
     * @param currentUserId 当前登录用户 ID（用以防止自删）
     * @throws com.baiflow.common.exception.BusinessException FORBIDDEN 试图删除自己或内置管理员
     */
    void batchDelete(List<String> ids, String currentUserId);

    /**
     * 获取当前已登录用户的个人信息（自服务）。
     *
     * @param userId 当前登录用户 ID
     * @return 用户信息（不含密码哈希）
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND 用户不存在
     */
    UserInfo me(String userId);

    /**
     * 更新当前用户的展示名称（自服务）。
     *
     * @param userId      当前登录用户 ID
     * @param displayName 新的展示名称（不允许为空）
     * @return 更新后的用户信息
     */
    UserInfo updateProfile(String userId, String displayName);

    /**
     * 上传/更新当前用户的头像（自服务）。文件大小 ≤1MB，仅支持 jpg/jpeg/png/gif/webp。
     *
     * @param userId 当前登录用户 ID
     * @param file   上传的头像文件
     * @return 更新后的用户信息（含新 avatarUrl）
     */
    UserInfo uploadAvatar(String userId, MultipartFile file);

    /**
     * 删除当前用户的头像（自服务）：删除文件 + 清空 avatarUrl，回到首字占位。
     *
     * @param userId 当前登录用户 ID
     * @return 更新后的用户信息（avatarUrl 为空）
     */
    UserInfo deleteAvatar(String userId);
}
