package com.baiflow.android.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/**
 * 音频 span — 文本按钮样式（无自绘图形）：用 ▶/⏸ 字符 + 「录音 mm:ss」文本表示。
 * {@code playing} / {@code durationMs} 由调用方更新后重绘（invalidate）。
 * 序列化时输出 {@code [录音](/api/notes/media/{mediaId}?mediaType=audio)}。
 */
public class NoteAudioSpan extends ReplacementSpan {

    private static final String PLAY = "▶";   // ▶
    private static final String PAUSE = "⏸";  // ⏸

    private final String mediaId;
    private final String mediaUrl;
    private final String alt;
    private final int textColor;
    private final int paddingH;

    private long durationMs = -1;   // 解析出的时长（ms），未知为 -1
    private boolean playing;        // 当前是否在播放

    public NoteAudioSpan(String mediaId, String mediaUrl, String alt,
                         int chipColor, int textColor, int paddingH, int chipHeight) {
        this.mediaId = mediaId;
        this.mediaUrl = mediaUrl;
        this.alt = alt != null ? alt : "录音";
        this.textColor = textColor;
        this.paddingH = paddingH;
        // chipColor / chipHeight 不再用于自绘（文本按钮样式），保留构造参数避免改动调用方
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

    /** 设置已解析时长（ms）并触发重绘（调用方负责 invalidate） */
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    /** 设置播放/暂停态并触发重绘（调用方负责 invalidate） */
    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        // 按固定宽度测量（时长用 00:00 占位），时长更新后宽度稳定，无需重新布局
        float w = paint.measureText(PLAY + " " + alt + "  00:00") + paddingH * 2;
        if (fm != null) {
            // 占位符字符本身可能无度量：显式用字体 metrics 撑起正常行高，
            // 否则行高塌陷 → 音频不可见、且内容总高度错乱导致滚动异常
            Paint.FontMetricsInt nf = paint.getFontMetricsInt();
            fm.top = nf.top;
            fm.ascent = nf.ascent;
            fm.descent = nf.descent;
            fm.bottom = nf.bottom;
        }
        return Math.round(w);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, Paint paint) {
        int oldColor = paint.getColor();
        paint.setColor(textColor);
        canvas.drawText(label(), x + paddingH, y, paint);
        paint.setColor(oldColor);
    }

    private String label() {
        return (playing ? PAUSE : PLAY) + " " + alt + "  " + formatDuration();
    }

    private String formatDuration() {
        if (durationMs < 0) {
            return "--:--";
        }
        long totalSec = durationMs / 1000;
        long m = totalSec / 60;
        long s = totalSec % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }
}
