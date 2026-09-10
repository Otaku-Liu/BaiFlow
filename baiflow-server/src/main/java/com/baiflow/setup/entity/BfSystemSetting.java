package com.baiflow.setup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统级设置实体 — 键值对存储，跨用户共享的单例配置。
 * <p>
 * 目前承载 {@code initialized_at}（系统首次初始化完成时间）。该标记**单向**：
 * 一旦写入不再清除，用于永久关闭首次初始化入口。
 */
@Data
@TableName("bf_system_setting")
public class BfSystemSetting {

    /** 主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 设置键，全局唯一 */
    private String settingKey;

    /** 设置值 */
    private String settingValue;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
