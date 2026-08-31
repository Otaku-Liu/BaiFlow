package com.baiflow.android.util;

import android.webkit.MimeTypeMap;

import java.util.Locale;

/** MIME 辅助：按扩展名猜 MIME（上传请求头 / 打开文件时选择系统应用共用） */
public final class MimeUtil {

    private MimeUtil() {
    }

    /** 按扩展名猜 MIME；猜不到回落通用二进制 */
    public static String guessFromName(String fileName) {
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (mime != null) {
                    return mime;
                }
            }
        }
        return "application/octet-stream";
    }
}
