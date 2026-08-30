package com.baiflow.uploadrecord.enums;

/** 上传来源客户端：Web 管理台 / Android 客户端 */
public enum UploadSource {
    WEB, ANDROID;

    /** 从 X-Device-Type 请求头解析；缺失/未知按 WEB */
    public static UploadSource fromDeviceType(String deviceType) {
        return "ANDROID".equalsIgnoreCase(deviceType) ? ANDROID : WEB;
    }
}
