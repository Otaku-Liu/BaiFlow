package com.baiflow.storage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStorageRootRequest(@NotBlank String name, @NotBlank String type, @NotBlank String rootPath, @NotNull Boolean readonly) {}
