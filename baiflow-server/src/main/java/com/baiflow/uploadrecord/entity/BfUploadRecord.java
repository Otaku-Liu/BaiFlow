package com.baiflow.uploadrecord.entity;

import com.baiflow.uploadrecord.enums.UploadSource;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件上传记录 — 记录一次上传动作，供传输历史与 ADMIN 审计。
 * <p>与下载记录（bf_download_record）对称；上传来源为客户端设备类型（WEB / ANDROID）。
 */
@Data
@TableName("bf_upload_record")
public class BfUploadRecord {

    /** 主键，UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 上传的文件 ID */
    private String fileId;

    /** 文件名快照（文件删除后仍可读） */
    private String fileName;

    /** 上传人用户 ID */
    private String uploaderUserId;

    /** 来源客户端：WEB / ANDROID */
    private UploadSource source;

    /** 上传 IP */
    private String ipAddress;

    /** 上传 User-Agent */
    private String userAgent;

    /** 上传时间 */
    private LocalDateTime createdAt;
}
