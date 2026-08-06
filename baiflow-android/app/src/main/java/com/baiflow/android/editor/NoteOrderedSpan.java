package com.baiflow.android.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.style.LeadingMarginSpan;

/**
 * 有序列表项 span — 在段首留白处绘制编号。编号仅供显示，序列化时从 1 重算，
 * 因此插入/删除后显示可能短暂陈旧，下次保存/打开自动校正。
 */
public class NoteOrderedSpan implements LeadingMarginSpan, NoteParagraphMarker {

    private final int marginPx;
    private final int color;
    private int number;

    public NoteOrderedSpan(int number, int marginPx, int color) {
        this.number = number;
        this.marginPx = marginPx;
        this.color = color;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getMargin() {
        return marginPx;
    }

    public int getColor() {
        return color;
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return marginPx;
    }

    @Override
    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline,
                                  int bottom, CharSequence text, int start, int end,
                                  boolean first, Layout layout) {
        if (!first) {
            return;
        }
        String label = number + ".";
        float textWidth = p.measureText(label);
        // 编号右对齐到留白区右侧（兼容 RTL 的 dir 符号）
        float tx = x + dir * (marginPx - textWidth - 4f);
        int oldColor = p.getColor();
        p.setColor(color);
        c.drawText(label, tx, baseline, p);
        p.setColor(oldColor);
    }
}
