package com.baiflow.user.entity;

import com.baiflow.user.enums.UserRole;
import com.baiflow.user.enums.UserStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体。
 */
@Data
@TableName("user")
public class User {

    /** 用户主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 登录用户名，全局唯一 */
    private String username;

    /** BCrypt 哈希后的密码，绝不存明文 */
    private String passwordHash;

    /** 显示名称 */
    private String displayName;

    /** 角色：ADMIN / USER / GUEST */
    private UserRole role;

    /** 状态：ACTIVE（正常）/ DISABLED（禁用）/ LOCKED（锁定） */
    private UserStatus status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
