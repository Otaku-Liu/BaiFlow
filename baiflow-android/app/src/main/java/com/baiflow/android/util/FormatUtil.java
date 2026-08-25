package com.baiflow.android.util;

/** 通用格式化工具 */
public final class FormatUtil {

    private FormatUtil() {
    }

    /** 字节数 → 可读大小（B/KB/MB/GB） */
    public static String formatSize(Long bytes) {
        if (bytes == null || bytes == 0) { return "0 B"; }
        if (bytes < 1024) { return bytes + " B"; }
        if (bytes < 1024 * 1024) { return String.format("%.1f KB", bytes / 1024.0); }
        if (bytes < 1024 * 1024 * 1024) { return String.format("%.1f MB", bytes / (1024.0 * 1024)); }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** 展示名首字占位（空则 "?"），按码点截取避免拆散代理对/emoji */
    public static String firstCharOrQuestion(String s) {
        if (s == null || s.isEmpty()) { return "?"; }
        return s.substring(0, s.offsetByCodePoints(0, 1));
    }
}
