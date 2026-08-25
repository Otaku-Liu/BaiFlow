package com.baiflow.android.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 图片工具 — 供头像上传使用：从 content:// 解码缩放、按 EXIF 校正方向、压缩到指定字节数内。
 */
public final class ImageUtil {

    private static final String TAG = "ImageUtil";

    private ImageUtil() {
    }

    /**
     * 从 content Uri 读图，等比缩放到 maxDimension 以内、按 EXIF 校正方向，压缩为 JPEG。
     *
     * @param maxDimension 最长边限制（px），头像取 512
     * @param maxBytes     输出字节上限，头像取 1MB（服务端上限）
     * @return JPEG 字节；读取失败或压缩后仍超限返回 null
     */
    @Nullable
    public static byte[] scaleDown(Context context, Uri uri, int maxDimension, int maxBytes) {
        try {
            Bitmap bitmap = decodeSampled(context, uri, maxDimension);
            if (bitmap == null) {
                return null;
            }
            bitmap = rotateByExif(context, uri, bitmap);

            byte[] jpeg = compress(bitmap, maxBytes);
            // 质量降到下限仍超限 → 等比再缩小后压缩（防御性兜底）
            if (jpeg == null && bitmap.getWidth() > 128) {
                int scale = Math.max(2, bitmap.getWidth() / 256);
                Bitmap smaller = Bitmap.createScaledBitmap(bitmap,
                        Math.max(1, bitmap.getWidth() / scale),
                        Math.max(1, bitmap.getHeight() / scale), true);
                if (smaller != bitmap) {
                    bitmap.recycle();
                }
                jpeg = compress(smaller, maxBytes);
                if (smaller != bitmap) {
                    smaller.recycle();
                }
            } else {
                bitmap.recycle();
            }
            return jpeg;
        } catch (Exception e) {
            Log.w(TAG, "scaleDown failed: " + uri, e);
            return null;
        }
    }

    /** 采样解码：最长边 ≤ maxDimension（inSampleSize 取 2 的幂） */
    private static Bitmap decodeSampled(Context context, Uri uri, int maxDimension) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) {
            sampleSize *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is, null, opts);
        }
    }

    /** 按 EXIF 方向旋转；无需旋转时原样返回 */
    private static Bitmap rotateByExif(Context context, Uri uri, Bitmap source) {
        int degrees = 0;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(is);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                degrees = 90;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                degrees = 180;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                degrees = 270;
            }
        } catch (IOException e) {
            Log.w(TAG, "read exif failed: " + uri, e);
        }
        if (degrees == 0) {
            return source;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        if (rotated != source) {
            source.recycle();
        }
        return rotated;
    }

    /** 逐步降质量压缩；降到底限仍超 maxBytes 返回 null */
    @Nullable
    private static byte[] compress(Bitmap bitmap, int maxBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int quality = 90; quality >= 40; quality -= 10) {
            out.reset();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            if (out.size() <= maxBytes) {
                return out.toByteArray();
            }
        }
        return null;
    }
}
