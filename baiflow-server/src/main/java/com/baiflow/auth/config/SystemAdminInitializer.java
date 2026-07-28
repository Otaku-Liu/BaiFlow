package com.baiflow.auth.config;

import com.baiflow.user.entity.User;
import com.baiflow.user.enums.UserRole;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
/**
 * 系统管理员初始化器 — 应用启动时检查并创建默认管理员账户。
 * <p>
 * 如果数据库中不存在配置的管理员用户，则根据 {@code baiflow.init-admin.*} 配置创建。
 * 若数据库表尚未就绪（Flyway 已禁用，需手动执行 DDL），则跳过初始化并记录警告。
 */
@Slf4j
@Component
public class SystemAdminInitializer implements CommandLineRunner {
    @Autowired
    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private BaiflowProperties baiflowProperties;
    @Override
    public void run(String... args) {
        String username = baiflowProperties.getInitAdmin().getUsername();
        User existing;
        try {
            existing = userMapper.selectByUsername(username);
        } catch (Exception e) {
            log.warn("无法查询管理员用户（数据库表可能尚未创建，请先执行 db/migration/ 下的 DDL 脚本）: {}",
                    e.getMessage());
            return;
        }
        if (existing != null) {
            // 确保现有管理员角色正确
            if (existing.getRole() != UserRole.ADMIN) {
                existing.setRole(UserRole.ADMIN);
                userMapper.updateById(existing);
                log.info("已将用户 '{}' 的角色修正为 ADMIN", username);
            } else {
                log.info("管理员用户 '{}' 已存在，跳过初始化", username);
            }
        User admin = new User();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(baiflowProperties.getInitAdmin().getPassword()));
        admin.setDisplayName("Administrator");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userMapper.insert(admin);
        log.info("已创建管理员用户 '{}' — 请立即修改默认密码", username);
    }
}
