package com.baiflow.android.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.style.LeadingMarginSpan;

/**
 * 无序列表项 span — 在段首留白处绘制实心圆点，行内文本不携带任何列表标记，
 * 序列化时由 {@link MarkdownEmitter} 输出 {@code - } 前缀。
 */
public class NoteBulletSpan implements LeadingMarginSpan, NoteParagraphMarker {

    private final int marginPx;
    private final int radius;
    private final int color;

    public NoteBulletSpan(int marginPx, int radius, int color) {
        this.marginPx = marginPx;
        this.radius = radius;
        this.color = color;
    }

    public int getMargin() {
        return marginPx;
    }

    public int getRadius() {
        return radius;
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
        int cx = x + dir * (marginPx / 2);
        int cy = (top + bottom) / 2;
        int oldColor = p.getColor();
        p.setColor(color);
        c.drawCircle(cx, cy, radius, p);
        p.setColor(oldColor);
    }
}
