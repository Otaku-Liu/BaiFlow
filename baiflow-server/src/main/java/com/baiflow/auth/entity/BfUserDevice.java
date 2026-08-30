package com.baiflow.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户登录设备登记 — 记录登录过的设备（按 user + device_name 唯一），登出不删，保留历史。
 * <p>在线状态由是否存在未过期会话（bf_auth_session）判定，本表只存登录历史与最近登录时间。
 */
@Data
@TableName("bf_user_device")
public class BfUserDevice {

    /** 主键，UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 归属用户 ID */
    private String userId;

    /** 设备名（App 机型 / Web 浏览器摘要），作为设备身份 */
    private String deviceName;

    /** 设备类型：ANDROID / WEB */
    private String deviceType;

    /** 首次登录时间 */
    private LocalDateTime firstLoginAt;

    /** 最近登录时间 */
    private LocalDateTime lastLoginAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
