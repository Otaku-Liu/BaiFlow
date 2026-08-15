package com.baiflow.android.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

/**
 * 图片 span — 替代文本中单个 {@code ￼} 占位符。持媒体元数据（mediaId/url/alt），
 * drawable 可被替换（未加载时显示占位，加载完成后换真实位图）。
 * 序列化时输出 {@code ![alt](/api/notes/media/{mediaId})}。
 */
public class NoteImageSpan extends ReplacementSpan {

    private final String mediaId;
    private final String mediaUrl;
    private final String alt;
    private final int maxWidthPx;
    private Drawable drawable;

    public NoteImageSpan(String mediaId, String mediaUrl, String alt, Drawable placeholder, int maxWidthPx) {
        this.mediaId = mediaId;
        this.mediaUrl = mediaUrl;
        this.alt = alt != null ? alt : "";
        this.maxWidthPx = maxWidthPx;
        this.drawable = placeholder;
        if (placeholder != null) {
            // GradientDrawable 等不报告 intrinsic size（-1），直接用会清空占位 bounds 导致完全不可见
            int w = placeholder.getIntrinsicWidth();
            int h = placeholder.getIntrinsicHeight();
            if (w > 0 && h > 0) {
                placeholder.setBounds(0, 0, w, h);
            } else if (placeholder.getBounds().width() <= 0 || placeholder.getBounds().height() <= 0) {
                // 无 intrinsic 也无 bounds：给默认尺寸兜底
                placeholder.setBounds(0, 0, Math.max(1, maxWidthPx),
                        Math.max(1, Math.round(maxWidthPx * 0.6f)));
            }
        }
    }

    public String getMediaId() {
        return mediaId;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getAlt() {
        return alt;
    }

    /** 加载完成后替换为真实位图（调用方按 span 范围重新定位后触发） */
    public void setBitmap(Bitmap bitmap, Context context) {
        if (bitmap == null) {
            return;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w > maxWidthPx && w > 0) {
            h = Math.max(1, (int) (h * (maxWidthPx / (float) w)));
            w = maxWidthPx;
        }
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, w, h, true);
        BitmapDrawable d = new BitmapDrawable(context.getResources(), scaled);
        d.setBounds(0, 0, w, h);
        this.drawable = d;
    }

    public void setDrawable(Drawable d) {
        this.drawable = d;
        if (d != null) {
            d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        }
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        Drawable d = drawable;
        if (d == null) {
            return 0;
        }
        Rect b = d.getBounds();
        if (fm != null) {
            // 图片底部对齐行基线区域；图片比行高时只向上扩展行高，
            // 避免旧算法把 ascent 算成正值导致图片上移、遮挡上一行内容
            int lineHeight = fm.descent - fm.ascent;
            int extra = Math.max(0, b.height() - lineHeight);
            fm.top = fm.top - extra;
            fm.ascent = fm.ascent - extra;
        }
        return b.width();
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, Paint paint) {
        Drawable d = drawable;
        if (d == null) {
            return;
        }
        canvas.save();
        int dy = bottom - d.getBounds().bottom;
        canvas.translate(x, dy);
        d.draw(canvas);
        canvas.restore();
    }
}
