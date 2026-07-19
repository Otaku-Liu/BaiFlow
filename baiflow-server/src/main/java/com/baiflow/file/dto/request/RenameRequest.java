package com.baiflow.file.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RenameRequest(@NotBlank String newName) {}
