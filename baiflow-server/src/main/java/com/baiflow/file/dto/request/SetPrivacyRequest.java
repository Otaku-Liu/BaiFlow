package com.baiflow.file.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SetPrivacyRequest(@NotBlank String password) {}
