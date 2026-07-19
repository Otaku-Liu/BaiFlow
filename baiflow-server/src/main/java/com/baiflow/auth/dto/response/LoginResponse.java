package com.baiflow.auth.dto.response;

import com.baiflow.user.dto.response.UserInfo;

public record LoginResponse(String token, UserInfo user) {
}
