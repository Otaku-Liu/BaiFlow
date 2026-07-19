package com.baiflow.storage.entity;

import com.baiflow.storage.enums.StorageRootStatus;
import com.baiflow.storage.enums.StorageRootType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 存储根目录实体 — 定义一个文件操作的安全边界。
 * <p>
 * 所有文件操作（浏览、上传、下载、移动、删除）必须限定在对应 Storage Root
 * 的磁盘路径范围内，禁止越界访问。
 */
@Data
@TableName("storage_root")
public class StorageRoot {

    /** 主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 存储根目录的显示名称 */
    private String name;

    /** 类型：LOCAL（本地磁盘）/ NAS_MOUNT（NAS 挂载） */
    private StorageRootType type;

    /** 磁盘上的绝对路径，作为所有文件操作的安全锚点 */
    private String rootPath;

    /** 状态：ACTIVE（可用）/ OFFLINE（离线）/ DISABLED（禁用） */
    private StorageRootStatus status;

    /** 是否只读（禁止写入、删除、移动操作） */
    private Boolean readonly;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
