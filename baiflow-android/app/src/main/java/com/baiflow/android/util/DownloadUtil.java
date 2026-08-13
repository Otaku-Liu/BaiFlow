package com.baiflow.android.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.baiflow.android.transfer.DownloadService;

/**
 * 下载工具 — 写公共 Download 目录的权限判断与下载前台服务启动。
 * <p>API 26-28 写公共目录需 WRITE_EXTERNAL_STORAGE 运行时权限；API 29+ 作用域存储走 MediaStore 无需权限。
 */
public final class DownloadUtil {

    private DownloadUtil() {
    }

    /** API 26-28 写公共下载目录需要 WRITE_EXTERNAL_STORAGE 运行时权限 */
    public static boolean needsLegacyStoragePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P;
    }

    public static boolean hasLegacyStoragePermission(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /** 启动下载前台服务（前台 Service 执行网络下载并写公共 Download 目录） */
    public static void startDownloadService(Context ctx, String fileId, String fileName, long sizeBytes) {
        Intent intent = new Intent(ctx, DownloadService.class);
        intent.putExtra(DownloadService.EXTRA_FILE_ID, fileId);
        intent.putExtra(DownloadService.EXTRA_FILE_NAME, fileName);
        intent.putExtra(DownloadService.EXTRA_SIZE_BYTES, sizeBytes);
        ctx.startForegroundService(intent);
    }
}
