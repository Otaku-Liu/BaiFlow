package com.baiflow.auth.service;

import com.baiflow.auth.dto.request.LoginRequest;
import com.baiflow.auth.dto.response.AuthSessionInfo;
import com.baiflow.auth.dto.response.LoginResponse;
import com.baiflow.user.dto.response.UserInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 认证服务 — 负责登录令牌签发和当前用户信息查询。
 */
public interface AuthService {

    /**
     * 验证用户名和密码进行登录。
     * <p>
     * 校验账号状态（禁用/锁定），对比 BCrypt 密码哈希。登录成功后更新最后登录时间，
     * 建登录会话（长会话 token），返回会话 token 和用户基本信息。
     *
     * @param request 登录凭据（用户名、密码）
     * @return 会话 token、会话信息与用户信息
     * @throws com.baiflow.common.exception.BusinessException INVALID_CREDENTIALS 用户名或密码错误
     * @throws com.baiflow.common.exception.BusinessException ACCOUNT_DISABLED   账号已被禁用
     * @throws com.baiflow.common.exception.BusinessException ACCOUNT_LOCKED    账号已被锁定
     */
    LoginResponse login(LoginRequest request);

    /**
     * 获取当前已认证用户的个人资料。
     *
     * @param userId JWT 中的用户 ID
     * @return 用户信息（不含密码哈希）
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND 用户不存在
     */
    UserInfo me(String userId);

    /**
     * 更新当前用户的展示名称。
     *
     * @param userId      当前用户 ID
     * @param displayName 新的展示名称
     * @return 更新后的用户信息
     */
    UserInfo updateProfile(String userId, String displayName);

    /**
     * 上传/更新当前用户的头像。
     *
     * @param userId 当前用户 ID
     * @param file   上传的头像文件（≤1MB，常用图片格式）
     * @return 更新后的用户信息（含新 avatarUrl）
     */
    UserInfo uploadAvatar(String userId, MultipartFile file);

    /**
     * 修改当前用户的密码，并吊销其**全部**登录会话（所有设备强制下线，重新登录）。
     *
     * @param userId      当前用户 ID
     * @param oldPassword 旧密码（用于验证身份）
     * @param newPassword 新密码
     * @throws com.baiflow.common.exception.BusinessException INVALID_CREDENTIALS 旧密码错误
     */
    void changePassword(String userId, String oldPassword, String newPassword);

    /**
     * 登出：吊销当前请求 token 对应的会话（立即生效）。
     *
     * @param token 请求携带的会话 token
     */
    void logout(String token);

    /**
     * 列出当前用户的登录会话（不含已吊销），标记当前会话。
     *
     * @param userId       当前用户 ID
     * @param currentToken 当前请求的会话 token（用于标记 current）
     * @return 会话列表（按最近使用倒序）
     */
    List<AuthSessionInfo> listSessions(String userId, String currentToken);

    /**
     * 强制下线某会话：本人任意会话或管理员任意用户会话。
     *
     * @param userId    当前用户 ID
     * @param isAdmin   是否管理员
     * @param sessionId 目标会话 ID
     */
    void revokeSession(String userId, boolean isAdmin, String sessionId);
}
