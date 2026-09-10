package com.baiflow.setup.config;

import com.baiflow.auth.config.BaiflowProperties;
import com.baiflow.setup.service.SystemSetupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 首次初始化入口准备器 — 启动时生成并展示一次性初始化令牌。
 * <p>
 * 系统尚未初始化时，令牌打印到启动日志，同时写入 {@code baiflow.setup.token-path}
 * 指定的文件（仅属主可读），供无法看日志的场景从数据目录读取。
 * 初始化完成后入口永久关闭，令牌文件随即删除。
 * <p>
 * 运行顺序在 {@link com.baiflow.auth.config.StorageRootInitializer} 之前。
 */
@Slf4j
@Component
@Order(0)
public class SetupTokenInitializer implements CommandLineRunner {

    @Autowired
    private SystemSetupService systemSetupService;

    @Autowired
    private BaiflowProperties baiflowProperties;

    @Override
    public void run(String... args) {
        String token = systemSetupService.prepareOnStartup();
        if (token == null) {
            return;
        }
        log.warn("""

                ======================================================================
                系统尚未完成首次初始化。
                请在浏览器打开 Web 端，按向导创建第一个管理员（地址为 Web 端根路径 /setup）。
                初始化令牌：{}
                也可从该文件读取：{}
                初始化完成后初始化入口将永久关闭，令牌随即失效。
                ======================================================================
                """, token, baiflowProperties.getSetup().getTokenPath());
    }
}
