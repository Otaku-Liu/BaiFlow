package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * 用户信息。
 */
public class UserInfo {
    @SerializedName("id")
    private String id;
    @SerializedName("username")
    private String username;
    @SerializedName("displayName")
    private String displayName;
    @SerializedName("role")
    private String role;

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
}
