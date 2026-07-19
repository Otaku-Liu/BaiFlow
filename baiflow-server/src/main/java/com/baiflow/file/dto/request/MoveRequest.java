package com.baiflow.file.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MoveRequest(@NotBlank String targetStorageRootId, @NotBlank String targetParentId) {}
