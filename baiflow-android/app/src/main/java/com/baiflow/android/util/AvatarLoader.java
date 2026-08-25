package com.baiflow.android.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 头像图片加载 — 头像为公开静态资源（无鉴权），直接 OkHttp 拉取并采样解码。
 * <p>供后台线程调用（我的页 / 修改资料页预览）；失败或非图片返回 null。
 */
public final class AvatarLoader {

    private static final String TAG = "AvatarLoader";
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private AvatarLoader() {
    }

    /** 同步拉取头像并采样解码（最长边 ≤256px，头像展示足够） */
    @Nullable
    public static Bitmap load(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            Request request = new Request.Builder().url(url).build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }
                byte[] bytes = response.body().bytes();
                return decodeSampled(bytes, 256);
            }
        } catch (IOException e) {
            Log.w(TAG, "load failed: " + url, e);
            return null;
        }
    }

    /** 把相对头像 URL（/avatars/...）补全为完整地址；http(s) 原样返回；无 URL 返回 null */
    @Nullable
    public static String resolveUrl(@Nullable String baseUrl, @Nullable String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return null;
        }
        if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            return avatarUrl;
        }
        return baseUrl != null ? baseUrl + avatarUrl : null;
    }

    /** 后台拉取头像并设置到 ImageView（主线程回调），供我的页/修改资料页共用 */
    public static void loadInto(@NonNull ImageView view, @Nullable String url,
                                @Nullable Runnable onSuccess, @Nullable Runnable onFailure) {
        if (url == null || url.isEmpty()) {
            if (onFailure != null) {
                onFailure.run();
            }
            return;
        }
        new Thread(() -> {
            Bitmap bmp = load(url);
            view.post(() -> {
                if (bmp != null) {
                    view.setImageBitmap(bmp);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } else if (onFailure != null) {
                    onFailure.run();
                }
            });
        }).start();
    }

    /** 采样解码到 ≤maxDimension */
    private static Bitmap decodeSampled(byte[] bytes, int maxDimension) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) {
            sampleSize *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
    }
}
