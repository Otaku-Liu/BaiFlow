package com.baiflow.android.editor;

import android.text.style.ParagraphStyle;

/**
 * 段落级格式标记 — 由标题/列表/代码块 span 实现，供 {@link SpanExtractor}
 * 通过 {@code getSpans(range, NoteParagraphMarker.class)} 一次性枚举所有段落格式。
 */
public interface NoteParagraphMarker extends ParagraphStyle {
}
