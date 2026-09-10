package com.baiflow.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "baiflow")
public class BaiflowProperties {

    private final AuthSession authSession = new AuthSession();
    private final Setup setup = new Setup();
    private final Storage storage = new Storage();

    /** 登录会话时长：ANDROID 长期（滑动，天）/ WEB 短期（固定，小时） */
    @Data
    public static class AuthSession {
        private int webHours = 2;
        private int androidDays = 180;
    }

    /** 首次初始化：一次性初始化令牌的存放位置（日志之外的第二来源） */
    @Data
    public static class Setup {
        private String tokenPath = "./setup-token.txt";
    }

    @Data
    public static class Storage {
        private String defaultRootPath = "./baiflow-files";
        /** 头像文件存储目录 */
        private String avatarPath = "./baiflow-files/avatars";
        /** 笔记媒体（图片/录音/画画）专用存储目录，独立于文件中心 */
        private String noteMediaPath = "./baiflow-files/notes-media";
    }

}
