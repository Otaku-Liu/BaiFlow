package com.baiflow.share.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 更新分享链接请求（均为可选字段） */
public record UpdateShareRequest(
        String status,
        String expiresAt,
        Integer maxViews,
        Integer maxDownloads,
        String extractionCode
) {}
