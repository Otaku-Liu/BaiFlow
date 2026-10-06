package com.baiflow.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * 认证与授权判定工具 —— 控制层与管理服务共用。
 * <p>
 * 管理员判定统一走 {@link #isAdmin(Authentication)}，不各写就地判断：
 * {@code auth} 为空或权限集合为空一律按「非管理员」处理。此前各控制器的就地版本
 * 有带守卫与不带守卫两种写法，其中不带守卫的会在主体为空时抛 NPE —— 统一后这类分歧不再可能出现。
 * <p>
 * 「属主或管理员」统一走 {@link #isOwnerOrAdmin}，各服务不再就地拼 {@code !isAdmin && !x.equals(y)}。
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * 当前认证主体是否持有 ROLE_ADMIN。
     * @param auth 认证主体，可为空
     * @return 持有 ROLE_ADMIN 为 true；{@code auth} 为空或权限集合为空为 false
     */
    public static boolean isAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为资源属主本人或管理员 —— 「属主或管理员」这一判定的唯一实现。
     * <p>只回答「能不能」，不决定失败动作：调用点自行选择抛 40301 还是静默跳过。
     * @param ownerId 资源属主 ID（为空一律视为非本人，不抛 NPE）
     * @param userId  当前用户 ID
     * @param isAdmin 当前用户是否管理员
     * @return 管理员或属主本人为 true
     */
    public static boolean isOwnerOrAdmin(String ownerId, String userId, boolean isAdmin) {
        return isAdmin || (ownerId != null && ownerId.equals(userId));
    }
}
