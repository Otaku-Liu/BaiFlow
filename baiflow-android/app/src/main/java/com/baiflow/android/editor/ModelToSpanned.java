package com.baiflow.android.editor;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.style.URLSpan;

import java.util.List;

/**
 * Doc 行内 → Spannable（块所见即所得渲染，经 {@link BlockRichText} 使用）。
 * 行内格式 {@code SPAN_EXCLUSIVE_EXCLUSIVE}；音频链接（url 带 mediaType=audio）
 * 转成 {@link NoteAudioSpan}，图片转成 {@link NoteImageSpan}（占位符字符）。
 */
public final class ModelToSpanned {

    /** 媒体 span 使用的替换占位符字符 */
    public static final char PLACEHOLDER = '￼';

    private ModelToSpanned() {
    }

    static void appendInlines(SpannableStringBuilder sb, List<DocModel.Inline> inlines, EditorStyle style) {
        for (DocModel.Inline in : inlines) {
            if (in instanceof DocModel.TextRun t) {
                sb.append(t.text());
            } else if (in instanceof DocModel.Bold b) {
                int st = sb.length();
                appendInlines(sb, b.children(), style);
                sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (in instanceof DocModel.Italic i) {
                int st = sb.length();
                appendInlines(sb, i.children(), style);
                sb.setSpan(new StyleSpan(Typeface.ITALIC), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (in instanceof DocModel.Strike stk) {
                int st = sb.length();
                appendInlines(sb, stk.children(), style);
                sb.setSpan(new StrikethroughSpan(), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (in instanceof DocModel.Underline u) {
                int st = sb.length();
                appendInlines(sb, u.children(), style);
                sb.setSpan(new UnderlineSpan(), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (in instanceof DocModel.InlineCode c) {
                int st = sb.length();
                sb.append(c.code());
                sb.setSpan(new NoteInlineCodeSpan(style.colors.inlineCodeBgColor,
                                style.colors.inlineCodeTextColor),
                        st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (in instanceof DocModel.Link l) {
                if (isAudioUrl(l.url())) {
                    int st = sb.length();
                    sb.append(PLACEHOLDER);
                    NoteAudioSpan span = new NoteAudioSpan(mediaIdOf(l.url()), l.url(), audioAlt(l),
                            style.colors.audioChipColor, style.colors.audioTextColor,
                            style.audioPaddingPx, style.audioChipHeightPx);
                    // 录音 URL 带 &duration=ms，重进笔记时直接恢复时长显示
                    span.setDurationMs(audioDurationFrom(l.url()));
                    sb.setSpan(span, st, st + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    int st = sb.length();
                    appendInlines(sb, l.children(), style);
                    sb.setSpan(new URLSpan(l.url()), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else if (in instanceof DocModel.Image img) {
                int st = sb.length();
                sb.append(PLACEHOLDER);
                NoteImageSpan span = new NoteImageSpan(mediaIdOf(img.url()), img.url(), img.alt(),
                        style.newImagePlaceholder(), style.maxImageWidthPx);
                sb.setSpan(span, st, st + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static boolean isAudioUrl(String url) {
        return url != null && url.contains("mediaType=audio");
    }

    /** 从音频 URL 的 &duration=ms 参数读时长（录音时写入，重进笔记恢复显示）；无则 -1 */
    static long audioDurationFrom(String url) {
        if (url == null) {
            return -1;
        }
        int q = url.indexOf("duration=");
        if (q < 0) {
            return -1;
        }
        int end = url.indexOf('&', q);
        String v = end < 0 ? url.substring(q + "duration=".length())
                : url.substring(q + "duration=".length(), end);
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 从 /api/notes/media/{id}[?...] 提取 mediaId */
    static String mediaIdOf(String url) {
        if (url == null) {
            return "";
        }
        String body = url;
        int q = body.indexOf('?');
        if (q >= 0) {
            body = body.substring(0, q);
        }
        int slash = body.lastIndexOf('/');
        return slash >= 0 ? body.substring(slash + 1) : body;
    }

    private static String audioAlt(DocModel.Link link) {
        StringBuilder sb = new StringBuilder();
        for (DocModel.Inline in : link.children()) {
            if (in instanceof DocModel.TextRun t) {
                sb.append(t.text());
            }
        }
        String label = sb.toString().trim();
        return label.isEmpty() ? "录音" : label;
    }
}
