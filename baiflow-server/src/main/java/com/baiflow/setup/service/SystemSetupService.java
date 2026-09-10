package com.baiflow.setup.service;

import com.baiflow.auth.dto.response.LoginResponse;
import com.baiflow.setup.dto.request.SystemSetupRequest;

/**
 * 系统初始化服务 — 负责首次部署创建第一个管理员，并永久关闭初始化入口。
 * <p>
 * 初始化状态由 {@code bf_system_setting} 表的 {@code initialized_at} 单向标记决定：
 * 一旦写入永不回退，因此删除/改名管理员都不会重新开放入口。
 */
public interface SystemSetupService {

    /**
     * 系统是否已完成首次初始化。
     * <p>数据库不可用时保守返回 {@code true}（fail-closed），避免误开放初始化入口。
     */
    boolean isInitialized();

    /**
     * 启动时准备初始化入口：
     * <ol>
     *   <li>若库中已存在管理员用户而初始化标记为空，视为已初始化并补写标记（兜底旧部署）</li>
     *   <li>否则准备一次性初始化令牌（优先复用令牌文件，重启不换，便于用户从容操作）</li>
     * </ol>
     *
     * @return 当前初始化令牌；已初始化时返回 {@code null}
     */
    String prepareOnStartup();

    /**
     * 首次初始化：校验令牌 → 创建 ADMIN 用户 → 写入初始化标记 → 作废令牌 → 直接签发登录会话。
     *
     * @param request 令牌与管理员账号信息
     * @return 会话 token、会话信息与用户信息（客户端可直接进入系统）
     * @throws com.baiflow.common.exception.BusinessException SETUP_TOKEN_INVALID       令牌不正确
     * @throws com.baiflow.common.exception.BusinessException SETUP_ALREADY_INITIALIZED 已完成初始化
     * @throws com.baiflow.common.exception.BusinessException SETUP_RATE_LIMITED        令牌错误次数过多
     * @throws com.baiflow.common.exception.BusinessException USERNAME_EXISTS           用户名已存在
     */
    LoginResponse initialize(SystemSetupRequest request);
}
