package com.baiflow.android.editor;

import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

/**
 * 标题段落 span — 携带级别（1~3），兼具语义（{@link #getLevel()} 供序列化）与
 * 视觉（放大字号 + 加粗，级别越高字号越大）。
 */
public class NoteHeadingSpan extends MetricAffectingSpan implements NoteParagraphMarker {

    private final int level;
    private final int color;

    public NoteHeadingSpan(int level, int color) {
        this.level = level;
        this.color = color;
    }

    public int getLevel() {
        return level;
    }

    public int getColor() {
        return color;
    }

    @Override
    public void updateMeasureState(TextPaint p) {
        p.setTextSize(p.getTextSize() * scaleFor(level));
        p.setFakeBoldText(true);
    }

    @Override
    public void updateDrawState(TextPaint p) {
        p.setTextSize(p.getTextSize() * scaleFor(level));
        p.setFakeBoldText(true);
        p.setColor(color);
    }

    private static float scaleFor(int level) {
        return switch (level) {
            case 1 -> 1.5f;
            case 2 -> 1.3f;
            default -> 1.15f;
        };
    }
}
