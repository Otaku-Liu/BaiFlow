package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * 登录请求。
 */
public class LoginRequest {
    @SerializedName("username")
    private String username;
    @SerializedName("password")
    private String password;

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
