package com.baiflow.setup.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 首次初始化请求 — 创建第一个管理员账号。
 *
 * @param setupToken  服务器启动日志中打印的一次性初始化令牌
 * @param username    管理员用户名
 * @param password    管理员密码
 * @param displayName 显示名称（可空，默认同用户名）
 */
public record SystemSetupRequest(@NotBlank String setupToken, @NotBlank String username,
                                 @NotBlank String password, String displayName) {
}
