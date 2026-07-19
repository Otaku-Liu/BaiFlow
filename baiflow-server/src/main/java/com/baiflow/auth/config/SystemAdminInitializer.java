package com.baiflow.auth.config;

import com.baiflow.user.entity.User;
import com.baiflow.user.enums.UserRole;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SystemAdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminInitializer.class);
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final BaiflowProperties baiflowProperties;

    public SystemAdminInitializer(UserMapper userMapper, PasswordEncoder passwordEncoder,
                                  BaiflowProperties baiflowProperties) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.baiflowProperties = baiflowProperties;
    }

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
