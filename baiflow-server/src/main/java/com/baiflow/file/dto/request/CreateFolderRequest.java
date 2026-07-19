package com.baiflow.file.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateFolderRequest(@NotBlank String storageRootId, String parentId, @NotBlank String name) {}
