package com.baiflow.share.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 公开分享访问时的验证请求 */
public record VerifyCodeRequest(@NotBlank String extractionCode) {}
