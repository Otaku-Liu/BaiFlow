package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * 文件/文件夹信息（对应后端 FileItemInfo）。
 */
public class FileItem {
    @SerializedName("id")
    private String id;
    @SerializedName("storageRootId")
    private String storageRootId;
    @SerializedName("parentId")
    private String parentId;
    @SerializedName("name")
    private String name;
    @SerializedName("itemType")
    private String itemType; // FILE or DIRECTORY
    @SerializedName("sizeBytes")
    private Long sizeBytes;
    @SerializedName("mimeType")
    private String mimeType;
    @SerializedName("privacyMode")
    private String privacyMode; // NORMAL or PRIVATE
    @SerializedName("status")
    private String status;
    @SerializedName("createdAt")
    private String createdAt;
    @SerializedName("updatedAt")
    private String updatedAt;

    public String getId() { return id; }
    public String getStorageRootId() { return storageRootId; }
    public String getParentId() { return parentId; }
    public String getName() { return name; }
    public String getItemType() { return itemType; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getMimeType() { return mimeType; }
    public String getPrivacyMode() { return privacyMode; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public boolean isDirectory() { return "DIRECTORY".equals(itemType); }
    public boolean isPrivate() { return "PRIVATE".equals(privacyMode); }
}
