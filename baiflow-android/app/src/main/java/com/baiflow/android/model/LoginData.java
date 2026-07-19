package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * 登录响应中的 data 字段。
 */
public class LoginData {
    @SerializedName("token")
    private String token;

    public String getToken() { return token; }
}
