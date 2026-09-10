package com.baiflow.android.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.baiflow.android.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 「服务器尚未初始化」的界面动作 —— 服务器设置页与登录页共用。
 * <p>
 * Android 不实现建号向导：初始化令牌只能从服务器侧获取（启动日志 / 数据目录），
 * 能做初始化的人必定有终端，所以这里统一引导去 Web 端 {@code /setup}。
 */
public final class ServerSetupUi {

    private ServerSetupUi() {}

    /** 首次初始化页地址（Web 端 /setup） */
    public static String setupUrl(String baseUrl) {
        return baseUrl + "/setup";
    }

    /** 提示服务器未初始化，并提供浏览器入口 */
    public static void showNotInitializedDialog(Activity activity, String baseUrl) {
        final String url = setupUrl(baseUrl);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.server_title)
                .setMessage(activity.getString(R.string.server_not_initialized, url))
                .setPositiveButton(R.string.server_open_browser, (d, w) -> openBrowser(activity, url))
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    /** 用浏览器打开初始化页；没有可用浏览器时提示 */
    public static void openBrowser(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, context.getString(R.string.common_browser_unavailable),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
