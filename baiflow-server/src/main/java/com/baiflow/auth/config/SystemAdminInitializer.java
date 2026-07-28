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

@Slf4j
@Component
public class SystemAdminInitializer implements CommandLineRunner {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private BaiflowProperties baiflowProperties;

    @Override
    public void run(String... args) {
        String username = baiflowProperties.getInitAdmin().getUsername();
        if (userMapper.selectByUsername(username) != null) {
            log.info("Admin user '{}' already exists, skipping initialization", username);
            return;
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(baiflowProperties.getInitAdmin().getPassword()));
        admin.setDisplayName("Administrator");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userMapper.insert(admin);
        log.info("Initialized admin user '{}' — change password immediately", username);
    }
}
