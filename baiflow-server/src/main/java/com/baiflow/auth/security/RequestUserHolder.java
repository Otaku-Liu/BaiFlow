package com.baiflow.auth.security;

import com.baiflow.user.entity.BfUser;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.function.Supplier;

/**
 * 本次请求已经读到的当前用户。
 * <p>
 * {@code SessionAuthenticationFilter} 每请求都要读一次用户表做鉴权，读到的实体挂在这里，
 * 下游 Service 需要同一行时直接复用 —— 数据库在远端，**同一行在同一请求里查两次就是白花一次往返**
 * （约 40–75ms），见 {@code docs/06-coding-standards.md}「数据库往返」。
 *
 * <p>与「缓存用户」不同：这里只是把本请求自己读到的那一行传下去，不跨请求、不过期、不改安全语义
 * （禁用/降权在下一次请求立即生效）。
 */
public final class RequestUserHolder {

    private static final String ATTR = RequestUserHolder.class.getName();

    private RequestUserHolder() {
    }

    /** 鉴权过滤器读到用户后挂入本请求 */
    static void bind(BfUser user) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            attrs.setAttribute(ATTR, user, RequestAttributes.SCOPE_REQUEST);
        }
    }

    /** 取本次请求已读到的当前用户；非 Web 线程或未挂时为 null */
    public static BfUser get() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return attrs.getAttribute(ATTR, RequestAttributes.SCOPE_REQUEST) instanceof BfUser user
                ? user : null;
    }

    /**
     * 按 id 取用户：若正好就是本次请求的当前用户则直接复用，否则回落到传入的查询。
     * <p>把「查库那一句」作为参数递进来，是为了让复用判断留在调用点上、一眼看得见。
     */
    public static BfUser getOrLoad(String userId, Supplier<BfUser> loader) {
        BfUser cached = get();
        if (cached != null && cached.getId().equals(userId)) {
            return cached;
        }
        return loader.get();
    }
}
