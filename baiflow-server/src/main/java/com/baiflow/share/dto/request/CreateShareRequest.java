package com.baiflow.share.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建分享链接请求
 *
 * @param targetFileItemId       被分享的文件/文件夹 ID
 * @param shareType              FILE 或 FOLDER
 * @param accessMode             VIEW 或 DOWNLOAD
 * @param expiresAt              ISO 时间字符串（可选，为空则永不过期）
 * @param maxViews               最大访问次数（0 不限制）
 * @param maxDownloads           最大下载次数（0 不限制）
 * @param extractionCode         提取码（可选，为空则不设提取码）
 */
public record CreateShareRequest(
        @NotBlank String targetFileItemId,
        @NotBlank String shareType,
        @NotBlank String accessMode,
        String expiresAt,
        int maxViews,
        int maxDownloads,
        String extractionCode
) {}
