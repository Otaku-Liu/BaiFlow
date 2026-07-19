package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 分页响应。
 */
public class PagedResult<T> {
    @SerializedName("records")
    private List<T> records;
    @SerializedName("page")
    private int page;
    @SerializedName("size")
    private int size;
    @SerializedName("total")
    private int total;

    public List<T> getRecords() { return records; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public int getTotal() { return total; }
}
