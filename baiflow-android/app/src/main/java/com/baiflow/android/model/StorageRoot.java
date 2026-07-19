package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * 存储根目录信息。
 */
public class StorageRoot {
    @SerializedName("id")
    private String id;
    @SerializedName("name")
    private String name;
    @SerializedName("type")
    private String type;
    @SerializedName("status")
    private String status;
    @SerializedName("readonly")
    private boolean readonly;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public boolean isReadonly() { return readonly; }

    @Override
    public String toString() { return name; }
}
