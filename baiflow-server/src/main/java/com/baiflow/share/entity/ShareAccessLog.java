package com.baiflow.share.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 分享访问日志实体 */
@Data
@TableName("share_access_log")
public class ShareAccessLog {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String shareLinkId;
    private String action;
    private String ipAddress;
    private String userAgent;
    private Boolean success;
    private String failureReason;
    private LocalDateTime createdAt;
}
