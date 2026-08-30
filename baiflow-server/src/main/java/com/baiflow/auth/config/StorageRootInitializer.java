package com.baiflow.auth.config;

import com.baiflow.storage.entity.BfStorageRoot;
import com.baiflow.storage.enums.StorageRootStatus;
import com.baiflow.storage.enums.StorageRootType;
import com.baiflow.storage.mapper.BfStorageRootMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 存储根目录初始化器 — 应用启动时检查并创建默认存储根目录。
 * <p>
 * 如果数据库中没有任何存储根目录，则根据 {@code baiflow.storage.default-root-path}
 * 配置自动创建一个 LOCAL 类型的默认根目录，同时确保磁盘路径存在。
 * <p>
 * 运行顺序在 {@link SystemAdminInitializer} 之后，确保管理员账户已就绪。
 */
@Slf4j
@Component
@Order(1)
public class StorageRootInitializer implements CommandLineRunner {

    @Autowired
    private BfStorageRootMapper storageRootMapper;

    @Autowired
    private BaiflowProperties baiflowProperties;

    @Override
    public void run(String... args) {
        // 检查是否有任何存储根目录
        long count;
        try {
            count = storageRootMapper.selectCount(new LambdaQueryWrapper<>());
        } catch (Exception e) {
            log.warn("无法查询存储根目录（数据库表可能尚未创建，请先执行 db/migration/ 下的 DDL 脚本）: {}",
                    e.getMessage());
            return;
        }

        if (count > 0) {
            log.info("已有 {} 个存储根目录，跳过初始化", count);
            return;
        }

        String rootPath = baiflowProperties.getStorage().getDefaultRootPath();
        Path path = Path.of(rootPath).toAbsolutePath().normalize();

        // 确保磁盘目录存在
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            log.error("无法创建存储根目录路径 {}: {}", path, e.getMessage());
            return;
        }

        // 创建默认存储根目录记录
        BfStorageRoot root = new BfStorageRoot();
        root.setName("默认存储");
        root.setType(StorageRootType.LOCAL);
        root.setRootPath(path.toString());
        root.setStatus(StorageRootStatus.ACTIVE);
        root.setReadonly(false);
        storageRootMapper.insert(root);

        log.info("已创建默认存储根目录: name='{}', path='{}', status=ACTIVE", root.getName(), path);
    }
}
