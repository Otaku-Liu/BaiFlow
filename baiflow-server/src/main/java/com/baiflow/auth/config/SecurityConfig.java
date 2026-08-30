package com.baiflow.auth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.baiflow.auth.security.SessionAuthenticationFilter;
import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.util.I18nUtil;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.LocaleResolver;

/**
 * Spring Security 配置。
 *
 * <p>默认所有 API 需要登录（登录会话 token），仅以下例外：
 * <ul>
 *   <li>健康检查、登录、公开分享 — 无需登录</li>
 *   <li>用户管理、存储根管理 — 仅 ADMIN</li>
 * </ul>
 *
 * <p>无状态会话，禁用 CSRF，通过 {@link SessionAuthenticationFilter} 逐请求校验登录会话。
 */
@Configuration
public class SecurityConfig {

    @Autowired
    private SessionAuthenticationFilter sessionAuthenticationFilter;
    @Autowired
    private I18nUtil i18nUtil;
    @Autowired
    private LocaleResolver localeResolver;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 未认证/会话过期 → 401（区别于「已登录但无权限」的 403，客户端据此回登录）
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"code\":" + ErrorCode.UNAUTHORIZED
                                    + ",\"message\":\"" + i18nUtil.translate("登录已过期，请重新登录", localeResolver.resolveLocale(request))
                                    + "\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        // 无需登录
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        // 头像为公开静态资源（页面 <img> 直接引用，生产由 nginx alias 服务）
                        .requestMatchers("/avatars/**").permitAll()
                        // 仅 ADMIN
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 当前用户自服务（个人资料/头像）— 任何登录用户，须先于 /api/users/** ADMIN 门禁匹配
                        .requestMatchers("/api/users/me/**").authenticated()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/storage-roots/active").authenticated()
                        .requestMatchers("/api/storage-roots/**").hasRole("ADMIN")
                        // 其余均需登录
                        .anyRequest().authenticated())
                // ---- 登录会话过滤器 ----
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
