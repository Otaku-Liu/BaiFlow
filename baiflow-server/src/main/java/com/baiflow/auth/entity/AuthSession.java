package com.baiflow.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录会话实体 — 长会话 token（只存 SHA-256 哈希）+ 设备信息。
 * <p>
 * 认证模型 2：每次请求校验本表（记录存在 && 未过期），吊销即删除记录；
 * ANDROID 会话滑动续期（180 天不活跃兜底），WEB 会话固定短时（约 2h）。
 */
@Data
@TableName("bf_auth_session")
public class AuthSession {
    /** 主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 用户 ID */
    private String userId;

    /** 设备名（App 机型 / Web 浏览器摘要） */
    private String deviceName;

    /** 设备类型：ANDROID / WEB */
    private String deviceType;

    /** 最近登录 IP */
    private String ip;

    /** User-Agent */
    private String userAgent;

    /** 会话 token 的 SHA-256（十六进制），不存明文 */
    private String tokenHash;

    /** 会话到期时间（ANDROID 每次使用顺延 / WEB 固定） */
    private LocalDateTime expiresAt;

    /** 最近使用时间（滑动续期基准） */
    private LocalDateTime lastUsedAt;

    private LocalDateTime createdAt;
}
