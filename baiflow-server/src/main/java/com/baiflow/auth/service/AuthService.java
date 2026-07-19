package com.baiflow.auth.service;

import com.baiflow.auth.dto.request.LoginRequest;
import com.baiflow.auth.dto.response.LoginResponse;
import com.baiflow.user.dto.response.UserInfo;

/**
 * 认证服务 — 负责登录令牌签发和当前用户信息查询。
 */
public interface AuthService {

    /**
     * 验证用户名和密码进行登录。
     * <p>
     * 校验账号状态（禁用/锁定），对比 BCrypt 密码哈希。登录成功后更新最后登录时间，
     * 返回签发的 JWT 令牌和用户基本信息。
     *
     * @param request 登录凭据（用户名、密码）
     * @return JWT 令牌和用户信息
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
}
