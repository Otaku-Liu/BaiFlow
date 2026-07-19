package com.baiflow.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "baiflow")
public class BaiflowProperties {

    private final Jwt jwt = new Jwt();
    private final InitAdmin initAdmin = new InitAdmin();
    private final Storage storage = new Storage();
    private final Aria2 aria2 = new Aria2();

    @Data
    public static class Jwt {
        private String secret = "replace-me";
        private long expireSeconds = 7200L;
    }

    @Data
    public static class InitAdmin {
        private String username = "admin";
        private String password = "admin";
    }

    @Data
    public static class Storage {
        private String defaultRootPath = "./baiflow-files";
    }

    @Data
    public static class Aria2 {
        /** aria2 JSON-RPC 接口地址 */
        private String url = "http://127.0.0.1:6800/jsonrpc";
        /** aria2 RPC 密钥 */
        private String secret = "";
        /** 下载状态同步间隔（毫秒） */
        private long syncIntervalMs = 5000;
    }
}
