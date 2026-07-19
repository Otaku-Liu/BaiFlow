package com.baiflow.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 隐私文件夹访问会话实体。
 * <p>
 * 记录用户在隐私文件夹密码验证成功后获得的短期访问会话，
 * 用于在有效期内免重复输入隐私密码。
 * <p>
 * 当隐私文件夹密码被修改后，该文件夹的所有已有会话将被清理，
 * 确保旧密码持有者无法继续访问。
 */
@Data
@TableName("private_folder_access")
public class PrivateFolderAccess {

    /** 主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 访问用户 ID */
    private String userId;

    /** 隐私文件夹 ID */
    private String fileItemId;

    /** BCrypt 哈希后的短期访问令牌 */
    private String accessTokenHash;

    /** 会话过期时间（短期有效，建议 30 分钟） */
    private LocalDateTime expiresAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
