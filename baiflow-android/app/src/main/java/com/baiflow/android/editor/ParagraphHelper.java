package com.baiflow.android.editor;

import android.text.Spanned;

/**
 * 编辑器段落工具 — 定位光标所在物理行区间、查询段落格式 span。
 * <p>
 * 注意 {@code Spannable.getSpans(p, p, cls)} 在折叠光标处查不到起始于 p 的 span，
 * 需用 {@code [p, p+1)}。
 */
public final class ParagraphHelper {

    private ParagraphHelper() {
    }

    /** 返回 offset 所在物理行的内容区间 [start, end)，不含行尾换行符 */
    public static int[] lineRange(CharSequence text, int offset) {
        int start = 0;
        for (int i = Math.min(offset, text.length()) - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') {
                start = i + 1;
                break;
            }
        }
        int end = text.length();
        for (int i = Math.min(offset, text.length()); i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                end = i;
                break;
            }
        }
        return new int[]{start, end};
    }

    /** 该区间（物理行）是否无任何非空白字符 */
    public static boolean isBlank(CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 光标所在行的标题级别（0 = 非标题） */
    public static int headingLevel(Spanned text, int offset) {
        int[] r = lineRange(text, offset);
        NoteHeadingSpan[] hs = text.getSpans(r[0], Math.min(r[1] + 1, text.length()), NoteHeadingSpan.class);
        return hs.length > 0 ? hs[0].getLevel() : 0;
    }

    /** 光标所在行是否无序列表项 */
    public static boolean isBullet(Spanned text, int offset) {
        int[] r = lineRange(text, offset);
        return text.getSpans(r[0], Math.min(r[1] + 1, text.length()), NoteBulletSpan.class).length > 0;
    }

    /** 光标所在行是否有序列表项 */
    public static boolean isOrdered(Spanned text, int offset) {
        int[] r = lineRange(text, offset);
        return text.getSpans(r[0], Math.min(r[1] + 1, text.length()), NoteOrderedSpan.class).length > 0;
    }

    /** 光标所在行是否引用 */
    public static boolean isQuote(Spanned text, int offset) {
        int[] r = lineRange(text, offset);
        return text.getSpans(r[0], Math.min(r[1] + 1, text.length()), android.text.style.QuoteSpan.class).length > 0;
    }

    /** 光标所在行是否代码块 */
    public static boolean isCodeBlock(Spanned text, int offset) {
        int[] r = lineRange(text, offset);
        return text.getSpans(r[0], Math.min(r[1] + 1, text.length()), NoteCodeBlockSpan.class).length > 0;
    }
}
