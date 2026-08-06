package com.baiflow.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "baiflow")
public class BaiflowProperties {

    private final AuthSession authSession = new AuthSession();
    private final InitAdmin initAdmin = new InitAdmin();
    private final Storage storage = new Storage();
    private final Aria2 aria2 = new Aria2();

    /** 登录会话时长：ANDROID 长期（滑动，天）/ WEB 短期（固定，小时） */
    @Data
    public static class AuthSession {
        private int webHours = 2;
        private int androidDays = 180;
    }

    @Data
    public static class InitAdmin {
        private String username = "admin";
        private String password = "admin";
    }

    @Data
    public static class Storage {
        private String defaultRootPath = "./baiflow-files";
        /** 头像文件存储目录 */
        private String avatarPath = "./baiflow-files/avatars";
        /** 笔记媒体（图片/录音/画画）专用存储目录，独立于文件中心 */
        private String noteMediaPath = "./baiflow-files/notes-media";
    }

    @Data
    public static class Aria2 {
        private String url = "http://127.0.0.1:6800/jsonrpc";
        private String secret = "";
        private long syncIntervalMs = 5000;
    }

}
