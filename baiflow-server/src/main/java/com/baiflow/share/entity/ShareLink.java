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
@TableName("share_link")
public class ShareLink {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String targetFileItemId;
    private String createdBy;
    private String tokenHash;
    private String extractionCodeHash;
    private ShareType shareType;
    private AccessMode accessMode;
    private LocalDateTime expiresAt;
    private Integer maxViews;
    private Integer viewCount;
    private Integer maxDownloads;
    private Integer downloadCount;
    private Boolean requirePrivatePassword;
    private ShareStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
