package com.baiflow.user.dto.request;

import com.baiflow.user.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(@NotBlank String username, @NotBlank String password, String displayName, @NotNull UserRole role) {}
