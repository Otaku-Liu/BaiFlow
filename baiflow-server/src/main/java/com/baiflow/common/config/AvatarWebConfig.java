package com.baiflow.common.config;

import com.baiflow.auth.config.BaiflowProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源映射：/avatars/** → 头像存储目录。
 * <p>生产环境由 nginx alias 直接服务 /avatars/（见 deploy/nginx.conf），请求到不了后端；
 * 此映射主要用于开发环境（Vite 将 /avatars 代理到后端）展示头像。仅暴露头像目录，不涉及其他存储路径。
 */
@Configuration
public class AvatarWebConfig implements WebMvcConfigurer {

    @Autowired
    private BaiflowProperties baiflowProperties;

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
