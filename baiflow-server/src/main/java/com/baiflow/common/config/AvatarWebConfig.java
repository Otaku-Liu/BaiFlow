package com.baiflow.common.config;

import com.baiflow.common.config.BaiflowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源映射：/avatars/** → 头像存储目录。
 * <p>生产环境由前置的静态资源服务（Nginx 等）直接服务 /avatars/，请求到不了后端；
 * 此映射主要用于开发环境（Vite 将 /avatars 代理到后端）展示头像。仅暴露头像目录，不涉及其他存储路径。
 */
@Configuration
@RequiredArgsConstructor
public class AvatarWebConfig implements WebMvcConfigurer {

    private final BaiflowProperties baiflowProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String avatarPath = baiflowProperties.getStorage().getAvatarPath();
        if (!avatarPath.endsWith("/")) {
            avatarPath += "/";
        }
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:" + avatarPath);
    }
}
