package com.baiflow.storage.dto.response;

/**
 * NAS 连通性检查结果。
 *
 * @param rootId     存储根目录 ID
 * @param name       存储根目录名称
 * @param rootPath   磁盘路径
 * @param accessible 是否可访问（路径存在且可读）
 * @param message    详细信息
 * @param status     检查后的状态
 */
public record NasCheckResult(
        String rootId,
        String name,
        String rootPath,
        boolean accessible,
        String message,
        String status
) {}
