package com.baiflow.share.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 分享访问日志实体 */
@Data
@TableName("bf_share_access_log")
public class BfShareAccessLog {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 分享链接 ID */
    private String shareLinkId;

    /** 操作类型：VIEW / DOWNLOAD / VERIFY_CODE / FAILED */
    private String action;

    /** 访问者 IP 地址 */
    private String ipAddress;

    /** 访问者 User-Agent */
    private String userAgent;

    /** 是否成功（1=成功，0=失败） */
    private Boolean success;

    /** 失败原因（success=0 时填写） */
    private String failureReason;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
