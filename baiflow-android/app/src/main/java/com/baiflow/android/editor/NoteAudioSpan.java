package com.baiflow.android.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/**
 * 音频 span — 替代文本中单个 {@code ￼} 占位符，绘制「▶ 录音」圆角胶囊。
 * 点击由 {@link RichEditText#onTouchEvent} 拦截播放。
 * 序列化时输出 {@code [录音](/api/notes/media/{mediaId}?mediaType=audio)}。
 */
public class NoteAudioSpan extends ReplacementSpan {

    private final String mediaId;
    private final String mediaUrl;
    private final String alt;
    private final int chipColor;
    private final int textColor;
    private final int paddingH;
    private final int chipHeight;

    public NoteAudioSpan(String mediaId, String mediaUrl, String alt,
                         int chipColor, int textColor, int paddingH, int chipHeight) {
        this.mediaId = mediaId;
        this.mediaUrl = mediaUrl;
        this.alt = alt != null ? alt : "录音";
        this.chipColor = chipColor;
        this.textColor = textColor;
        this.paddingH = paddingH;
        this.chipHeight = chipHeight;
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

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float labelW = paint.measureText(alt);
        int w = (int) (paddingH * 2 + labelW + chipHeight);
        if (fm != null) {
            int ascent = fm.ascent;
            int offset = (fm.descent - fm.ascent - chipHeight) / 2;
            fm.ascent = ascent - offset;
            fm.top = fm.top - offset;
            fm.descent = ascent + chipHeight + offset;
            fm.bottom = fm.top + chipHeight + 2 * offset;
        }
        return w;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, Paint paint) {
        RectF chip = new RectF(x, (top + bottom - chipHeight) / 2f,
                x + getSize(paint, text, start, end, null), (top + bottom + chipHeight) / 2f);
        int oldColor = paint.getColor();

        paint.setColor(chipColor);
        canvas.drawRoundRect(chip, chipHeight / 2f, chipHeight / 2f, paint);

        // 播放三角
        paint.setColor(textColor);
        float triH = chipHeight * 0.4f;
        float cx = chip.left + paddingH + chipHeight * 0.35f;
        float cy = chip.centerY();
        Path path = new Path();
        path.moveTo(cx, cy - triH);
        path.lineTo(cx, cy + triH);
        path.lineTo(cx + triH * 0.9f, cy);
        path.close();
        canvas.drawPath(path, paint);

        // 标签
        paint.setTextSize(paint.getTextSize() * 0.9f);
        canvas.drawText(alt, chip.left + paddingH * 2 + chipHeight * 0.6f, cy + 1, paint);
        paint.setColor(oldColor);
    }
}
