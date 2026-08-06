package com.baiflow.android.editor;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.QuoteSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;

import java.util.List;

/**
 * Doc → SpannableStringBuilder（编辑器状态）。
 * <p>
 * 段落格式用 {@link Spanned.SPAN_EXCLUSIVE_EXCLUSIVE}（标题/列表/引用，单行段落模型）；
 * 代码块跨多行，用 {@link Spanned#SPAN_EXCLUSIVE_INCLUSIVE} 固定区间。
 * 行内格式 {@code SPAN_EXCLUSIVE_EXCLUSIVE}。音频链接（url 带 mediaType=audio）
 * 转成可播放的 {@link NoteAudioSpan}，图片转成 {@link NoteImageSpan}（占位符字符）。
 */
public final class ModelToSpanned {

    /** 媒体 span 使用的替换占位符字符 */
    public static final char PLACEHOLDER = '￼';

    private ModelToSpanned() {
    }

    public static SpannableStringBuilder toSpannable(DocModel.Doc doc, EditorStyle style) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (DocModel.Block b : doc.blocks()) {
            if (b instanceof DocModel.BlankBlock) {
                sb.append('\n');
            } else if (b instanceof DocModel.TextBlock tb) {
                appendInlines(sb, tb.inlines(), style);
                sb.append('\n');
            } else if (b instanceof DocModel.HeadingBlock h) {
                int st = sb.length();
                appendInlines(sb, h.inlines(), style);
                sb.append('\n');
                sb.setSpan(new NoteHeadingSpan(h.level(), style.colors.headingColor),
                        st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (b instanceof DocModel.BulletListBlock bl) {
                for (List<DocModel.Inline> item : bl.items()) {
                    int st = sb.length();
                    appendInlines(sb, item, style);
                    sb.append('\n');
                    sb.setSpan(new NoteBulletSpan(style.listMarginPx, style.bulletRadiusPx,
                                    style.colors.bulletColor),
                            st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else if (b instanceof DocModel.OrderedListBlock ol) {
                int n = 1;
                for (List<DocModel.Inline> item : ol.items()) {
                    int st = sb.length();
                    appendInlines(sb, item, style);
                    sb.append('\n');
                    sb.setSpan(new NoteOrderedSpan(n++, style.listMarginPx, style.colors.orderedColor),
                            st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else if (b instanceof DocModel.QuoteBlock q) {
                int st = sb.length();
                appendInlines(sb, q.inlines(), style);
                sb.append('\n');
                sb.setSpan(new QuoteSpan(style.colors.quoteColor), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (b instanceof DocModel.CodeBlock cb) {
                int st = sb.length();
                sb.append(cb.code());
                if (sb.length() == st || sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                sb.setSpan(new NoteCodeBlockSpan(cb.language(), style.codeMarginPx,
                                style.colors.codeBgColor, style.colors.codeGutterColor),
                        st, sb.length(), Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
            }
        }
        return sb;
    }

    private static void appendInlines(SpannableStringBuilder sb, List<DocModel.Inline> inlines, EditorStyle style) {
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

    /** 判断是否为音频链接（供 SpanExtractor 复用） */
    static boolean isAudioReference(String url) {
        return isAudioUrl(url);
    }

    /** Spannable 是否为可编辑（占位符字符） */
    static boolean isPlaceholderChar(char c) {
        return c == PLACEHOLDER;
    }
}
