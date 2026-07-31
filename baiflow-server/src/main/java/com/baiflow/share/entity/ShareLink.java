package com.baiflow.share.entity;

import com.baiflow.share.enums.ShareStatus;
import com.baiflow.share.enums.ShareType;
import com.baiflow.share.enums.AccessMode;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 分享链接实体 */
@Data
@TableName("bf_share_link")
public class ShareLink {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 被分享的文件或文件夹 ID */
    private String targetFileItemId;

    /** 创建者用户 ID */
    private String createdBy;

    /** 创建者用户名快照（用户删除后保留） */
    private String ownerUsername;

    /** 创建者展示名快照（用户删除后保留） */
    private String ownerDisplayName;

    /** BCrypt 哈希后的分享 token */
    private String tokenHash;

    /** BCrypt 哈希后的提取码（空字符串表示未设置提取码） */
    private String extractionCodeHash;

    /** 分享类型：FILE / FOLDER */
    private ShareType shareType;

    /** 访问模式：VIEW（浏览）/ DOWNLOAD（可下载） */
    private AccessMode accessMode;

    /** 过期时间（NULL 表示永不过期） */
    private LocalDateTime expiresAt;

    /** 最大访问次数（0 表示不限制） */
    private Integer maxViews;

    /** 已访问次数 */
    private Integer viewCount;

    /** 最大下载次数（0 表示不限制） */
    private Integer maxDownloads;

    /** 已下载次数 */
    private Integer downloadCount;

    /** 是否需要隐私文件夹密码（分享目标是隐私文件夹时为 1） */
    private Boolean requirePrivatePassword;

    /** 状态：ACTIVE / EXPIRED / REVOKED */
    private ShareStatus status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
