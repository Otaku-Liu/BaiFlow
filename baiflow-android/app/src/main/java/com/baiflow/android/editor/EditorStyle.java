package com.baiflow.android.editor;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/**
 * 编辑器样式/度量集中持有 — 由 Activity 从 Context 构建一次，供
 * {@link ModelToSpanned} 与工具栏操作统一使用（色值走资源，度量按 density 换算）。
 */
public final class EditorStyle {

    private final float density;
    public final EditorColors colors;
    public final int listMarginPx;
    public final int bulletRadiusPx;
    public final int codeMarginPx;
    public final int audioPaddingPx;
    public final int audioChipHeightPx;
    public final int maxImageWidthPx;

    public EditorStyle(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        colors = new EditorColors(context);
        listMarginPx = Math.round(20 * density);
        bulletRadiusPx = Math.max(3, Math.round(3 * density));
        codeMarginPx = Math.round(12 * density);
        audioPaddingPx = Math.round(8 * density);
        audioChipHeightPx = Math.round(22 * density);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        maxImageWidthPx = Math.max(200, screenWidth - Math.round(48 * density));
    }

    /** 图片未加载完成时的占位 drawable（灰底圆角块） */
    public Drawable newImagePlaceholder() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(colors.imagePlaceholderColor);
        gd.setCornerRadius(6 * density);
        int w = maxImageWidthPx;
        int h = Math.round(maxImageWidthPx * 0.6f);
        gd.setBounds(0, 0, w, h);
        return gd;
    }
}
