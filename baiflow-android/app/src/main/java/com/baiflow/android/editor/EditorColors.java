package com.baiflow.android.editor;

import android.content.Context;

import com.baiflow.android.R;

/**
 * 编辑器用色集中持有 — 从资源读取一次，供 span 构造使用，避免散落硬编码色值。
 */
public final class EditorColors {

    public final int headingColor;
    public final int bulletColor;
    public final int orderedColor;
    public final int quoteColor;
    public final int codeBgColor;
    public final int codeGutterColor;
    public final int codeTextColor;
    public final int inlineCodeBgColor;
    public final int inlineCodeTextColor;
    public final int imagePlaceholderColor;
    public final int audioChipColor;
    public final int audioTextColor;

    public EditorColors(Context context) {
        headingColor = context.getColor(R.color.text_primary);
        bulletColor = context.getColor(R.color.text_secondary);
        orderedColor = context.getColor(R.color.text_secondary);
        quoteColor = context.getColor(R.color.accent);
        codeBgColor = context.getColor(R.color.code_bg);
        codeGutterColor = context.getColor(R.color.divider);
        codeTextColor = context.getColor(R.color.text_primary);
        inlineCodeBgColor = context.getColor(R.color.inline_code_bg);
        inlineCodeTextColor = context.getColor(R.color.text_primary);
        imagePlaceholderColor = context.getColor(R.color.divider);
        audioChipColor = context.getColor(R.color.accent);
        audioTextColor = context.getColor(R.color.white);
    }
}
