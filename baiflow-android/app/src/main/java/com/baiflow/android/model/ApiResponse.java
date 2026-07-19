package com.baiflow.android.model;

import com.google.gson.annotations.SerializedName;

/**
 * API 统一响应包装。
 */
public class ApiResponse<T> {
    @SerializedName("code")
    private String code;
    @SerializedName("message")
    private String message;
    @SerializedName("data")
    private T data;
    @SerializedName("traceId")
    private String traceId;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getTraceId() { return traceId; }

    public boolean isOk() { return "OK".equals(code); }
}
