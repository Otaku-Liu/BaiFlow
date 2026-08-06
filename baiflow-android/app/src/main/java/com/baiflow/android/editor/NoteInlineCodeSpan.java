package com.baiflow.android.editor;

import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

/**
 * 行内代码 span — 等宽字体 + 浅色底块。序列化时输出 {@code `code`}。
 */
public class NoteInlineCodeSpan extends MetricAffectingSpan {

    private final int bgColor;
    private final int textColor;

    public NoteInlineCodeSpan(int bgColor, int textColor) {
        this.bgColor = bgColor;
        this.textColor = textColor;
    }

    @Override
    public void updateMeasureState(TextPaint p) {
        p.setTypeface(Typeface.MONOSPACE);
    }

    @Override
    public void updateDrawState(TextPaint p) {
        p.setTypeface(Typeface.MONOSPACE);
        p.setColor(textColor);
        p.bgColor = bgColor;
    }
}
