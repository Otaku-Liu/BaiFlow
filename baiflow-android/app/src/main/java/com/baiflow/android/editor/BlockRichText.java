package com.baiflow.android.editor;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

/**
 * 块级所见即所得往返 — 单个文本块 EditText 与「行内 markdown 源」互转。
 * <p>
 * 渲染：{@link MarkdownParser#parseInlines} → {@link ModelToSpanned#appendInlines}
 * （加粗/斜体/下划线/删除线/行内码/链接渲染为对应 span，编辑即预览）；
 * 回写：{@link SpanExtractor#extractInlines} → {@link MarkdownEmitter#emitInlines}
 * （把编辑后的 Spannable 还原为 markdown 存 b.text）。
 * 与 Web 端所见即所得对齐：存储仍是 md 源，块内显示渲染后的格式效果。
 */
public final class BlockRichText {

    private BlockRichText() {
    }

    /** 行内 markdown → Spannable（块编辑框显示用） */
    public static Spannable toSpannable(String inlineMd, EditorStyle style) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        ModelToSpanned.appendInlines(sb, MarkdownParser.parseInlines(inlineMd), style);
        return sb;
    }

    /** Spannable → 行内 markdown（编辑后回写 b.text） */
    public static String toMarkdown(Spanned s) {
        return MarkdownEmitter.emitInlines(SpanExtractor.extractInlines(s, 0, s.length()));
    }
}
