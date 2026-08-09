package com.baiflow.downloadrecord.entity;

import com.baiflow.downloadrecord.enums.DownloadSource;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件下载记录 — 记录一次下载动作，供文件中心下载次数统计与 ADMIN 审计。
 * <p>登录用户直接下载（CLIENT）记录下载人；分享链接下载（SHARE）下载人为空，关联分享 ID。
 */
@Data
@TableName("bf_download_record")
public class DownloadRecord {

    /** 主键，UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 被下载的文件 ID */
    private String fileId;

    /** 文件名快照（文件删除后仍可读） */
    private String fileName;

    /** 下载人用户 ID（分享匿名下载为 null） */
    private String downloaderUserId;

    /** 来源：CLIENT（登录直接下载）/ SHARE（分享链接下载） */
    private DownloadSource source;

    /** 来源分享链接 ID（非分享下载为 null） */
    private String shareId;

    /** 下载 IP */
    private String ipAddress;

    /** 下载 User-Agent */
    private String userAgent;

    /** 下载时间 */
    private LocalDateTime createdAt;
}
