package com.baiflow.download.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建下载任务请求。
 *
 * @param sourceUrl            下载源 URL（必填）
 * @param targetStorageRootId  目标存储根目录 ID（必填）
 * @param targetRelativePath   下载文件存放的子路径（可选，为空则放根目录）
 */
public record CreateDownloadRequest(
        @NotBlank String sourceUrl,
        @NotBlank String targetStorageRootId,
        String targetRelativePath
) {}
