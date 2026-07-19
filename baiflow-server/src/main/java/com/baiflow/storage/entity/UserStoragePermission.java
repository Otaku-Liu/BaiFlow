package com.baiflow.storage.entity;

import com.baiflow.storage.enums.PermissionLevel;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户存储权限实体 — 定义用户对存储根目录或特定文件/文件夹的访问级别。
 * <p>
 * file_item_id 为 null 时表示对整个 Storage Root 的授权；
 * 指定 file_item_id 时表示对特定目录或文件的授权。
 */
@Data
@TableName("user_storage_permission")
public class UserStoragePermission {

    /** 主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 被授权的用户 ID */
    private String userId;

    /** 授权的存储根目录 ID */
    private String storageRootId;

    /** 授权的具体文件或目录 ID（null 表示整个存储根目录） */
    private String fileItemId;

    /** 权限级别：READ（只读）/ WRITE（读写）/ MANAGE（管理） */
    private PermissionLevel permission;

    /** 授权创建者 ID */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
