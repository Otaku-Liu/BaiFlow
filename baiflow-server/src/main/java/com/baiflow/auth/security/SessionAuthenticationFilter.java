package com.baiflow.auth.security;

import com.baiflow.auth.entity.AuthSession;
import com.baiflow.user.entity.User;
import com.baiflow.user.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话 token 认证过滤器（模型 2）— 逐请求校验 {@code bf_auth_session}。
 * <p>
 * 支持 Bearer 头与 {@code ?token=} 查询参数（后者供 {@code <img>/<video>}/SSE 等
 * 浏览器直接请求，沿旧 JWT 过滤器的双通道）。
 * 校验：记录存在 && 未过期；ANDROID/WEB 会话滑动续期（节流 1h 写库）；role 取用户表当前值。
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private SessionTokenService sessionTokenService;
    @Autowired
    private UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = AuthTokens.extract(request);
        if (token != null) {
            AuthSession session = sessionTokenService.findByToken(token);
            LocalDateTime now = LocalDateTime.now();
            if (session != null
                    && session.getExpiresAt() != null && session.getExpiresAt().isAfter(now)) {
                User user = userMapper.selectById(session.getUserId());
                if (user != null) {
                    // 滑动续期：距上次续期超过 1 小时则写库顺延（ANDROID / WEB 通用）
                    if (session.getLastUsedAt() != null
                            && Duration.between(session.getLastUsedAt(), now)
                                    .compareTo(sessionTokenService.touchInterval()) > 0) {
                        sessionTokenService.touch(session);
                    }
                    List<SimpleGrantedAuthority> authorities =
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user.getId(), null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
