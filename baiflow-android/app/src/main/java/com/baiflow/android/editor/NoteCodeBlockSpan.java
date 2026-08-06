package com.baiflow.android.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;

/**
 * 围栏代码块 span — 覆盖整块（可跨多行），背景着色 + 左侧色条。语言仅存元数据
 * （序列化时输出围栏信息字符串），正文内容原样透传。
 */
public class NoteCodeBlockSpan implements LeadingMarginSpan, LineBackgroundSpan, NoteParagraphMarker {

    private final int marginPx;
    private final int bgColor;
    private final int gutterColor;
    private final String language;

    public NoteCodeBlockSpan(String language, int marginPx, int bgColor, int gutterColor) {
        this.language = language != null ? language : "";
        this.marginPx = marginPx;
        this.bgColor = bgColor;
        this.gutterColor = gutterColor;
    }

    public String getLanguage() {
        return language;
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
        // 左侧色条
        int oldColor = p.getColor();
        p.setColor(gutterColor);
        c.drawRect(x, top, x + dir * (marginPx / 2), bottom, p);
        p.setColor(oldColor);
    }

    @Override
    public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline,
                               int bottom, CharSequence text, int start, int end, int lnum) {
        int oldColor = p.getColor();
        p.setColor(bgColor);
        c.drawRect(left, top, right, bottom, p);
        p.setColor(oldColor);
    }
}
