package com.baiflow.user.dto.request;

import com.baiflow.user.enums.UserRole;
import com.baiflow.user.enums.UserStatus;

public record UpdateUserRequest(String displayName, UserRole role, UserStatus status) {}
