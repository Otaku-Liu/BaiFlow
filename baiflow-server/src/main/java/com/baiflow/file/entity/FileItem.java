package com.baiflow.file.entity;

import com.baiflow.file.enums.FileItemStatus;
import com.baiflow.file.enums.ItemType;
import com.baiflow.file.enums.PrivacyMode;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件项实体 — 代表文件系统中的文件和目录元数据。
 * <p>
 * 文件本体存储在磁盘上（由 Storage Root 管理的路径），此表仅存储元数据。
 * 使用软删除策略：status 标记为 DELETED 时记录删除时间，磁盘清理可异步进行。
 */
@Data
@TableName("file_item")
public class FileItem {

    /** 主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属存储根目录 ID */
    private String storageRootId;

    /** 父目录 ID（null 表示该存储根目录的根层级） */
    private String parentId;

    /** 文件/目录的所有者用户 ID */
    private String ownerUserId;

    /** 文件/目录名称（不含路径） */
    private String name;

    /** 相对于存储根目录的路径 */
    private String relativePath;

    /** 类型：FILE（文件）/ DIRECTORY（目录） */
    private ItemType itemType;

    /** 文件大小（字节），目录为 0 */
    private Long sizeBytes;

    /** MIME 类型（目录为空字符串） */
    private String mimeType;

    /** SHA-256 哈希值（目录为空字符串） */
    private String hashSha256;

    /** 隐私模式：NORMAL（正常可见）/ PRIVATE（需额外密码） */
    private PrivacyMode privacyMode;

    /** BCrypt 哈希后的隐私访问密码（仅 PRIVATE 模式的目录有值） */
    private String privacyPasswordHash;

    /** 状态：ACTIVE（正常）/ DELETED（已删除） */
    private FileItemStatus status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 删除时间（软删除时写入） */
    private LocalDateTime deletedAt;
}
