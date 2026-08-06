package com.baiflow.android.editor;

import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.QuoteSpan;

/**
 * 回车处理（TextWatcher）— 处理两类行为：
 * <ol>
 * <li><b>段落 span 回收</b>：段落 span 用 EXCLUSIVE_EXCLUSIVE 覆盖「行内容 + 行尾换行」，
 *     回车插入 {@code '\n'} 会让旧 span 顺势盖住两行，这里把上一行的段落 span 重新收窄到
 *     仅覆盖第一行（[行首, newlinePos+1)）。</li>
 * <li><b>列表/引用延续</b>：上一行是列表项且非空 → 新行延续同类型列表 span；上一行为空项
 *     → 移除列表 span 退出列表；引用同理（空行退出）。</li>
 * </ol>
 * 判断依据：本次改动恰好插入一个 {@code '\n'}（软键盘 commitText 与硬键盘 key 事件都会触发）。
 */
public final class ListKeyListener implements TextWatcher {

    private int changeStart = -1;
    private int lastLength = -1;
    private Object preEmptyListSpan;   // 回车前光标所在空列表项（回车后移除=退出列表）

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        changeStart = start;
        lastLength = s.length();
        preEmptyListSpan = null;
        // 空项回车退出列表：EXCLUSIVE_EXCLUSIVE 会把空行上的列表 span 移到新行，
        // 必须在插入前记录该 span，回车后按引用移除
        if (count == 0 && after == 1) {
            int[] line = ParagraphHelper.lineRange(s, start);
            if (ParagraphHelper.isBlank(s, line[0], Math.min(line[1], s.length()))) {
                int scanEnd = Math.min(line[1] + 1, s.length());
                NoteBulletSpan b = first((Spanned) s, line[0], scanEnd, NoteBulletSpan.class);
                if (b != null) {
                    preEmptyListSpan = b;
                    return;
                }
                NoteOrderedSpan o = first((Spanned) s, line[0], scanEnd, NoteOrderedSpan.class);
                if (o != null) {
                    preEmptyListSpan = o;
                }
            }
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (changeStart < 0 || lastLength < 0) {
            return;
        }
        int p = changeStart;
        if (s.length() != lastLength + 1 || p >= s.length() || s.charAt(p) != '\n') {
            changeStart = -1;
            lastLength = -1;
            preEmptyListSpan = null;
            return;
        }
        if (preEmptyListSpan != null) {
            // 空项回车：移除列表 span（退出列表）
            s.removeSpan(preEmptyListSpan);
            preEmptyListSpan = null;
            changeStart = -1;
            lastLength = -1;
            return;
        }
        handleEnter(s, p);
        changeStart = -1;
        lastLength = -1;
    }

    private void handleEnter(Editable s, int newlinePos) {
        int[] prev = ParagraphHelper.lineRange(s, Math.max(0, newlinePos - 1));
        boolean prevBlank = ParagraphHelper.isBlank(s, prev[0], Math.min(prev[1], newlinePos));
        int scanEnd = Math.min(newlinePos + 1, s.length());

        // 1. 标题：收窄到第一行，新行普通（不延续）
        NoteHeadingSpan heading = first(s, prev[0], scanEnd, NoteHeadingSpan.class);
        if (heading != null) {
            s.removeSpan(heading);
            s.setSpan(new NoteHeadingSpan(heading.getLevel(), heading.getColor()),
                    prev[0], newlinePos + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return;
        }

        // 2. 无序列表：空项回车退出；否则收窄上一行 + 新行延续
        NoteBulletSpan bullet = first(s, prev[0], scanEnd, NoteBulletSpan.class);
        if (bullet != null) {
            s.removeSpan(bullet);
            if (!prevBlank) {
                s.setSpan(new NoteBulletSpan(bullet.getMargin(), bullet.getRadius(), bullet.getColor()),
                        prev[0], newlinePos + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                applyBullet(s, newlinePos, bullet.getMargin(), bullet.getRadius(), bullet.getColor());
            }
            return;
        }

        // 3. 有序列表：同无序列表
        NoteOrderedSpan ordered = first(s, prev[0], scanEnd, NoteOrderedSpan.class);
        if (ordered != null) {
            s.removeSpan(ordered);
            if (!prevBlank) {
                s.setSpan(new NoteOrderedSpan(ordered.getNumber(), ordered.getMargin(), ordered.getColor()),
                        prev[0], newlinePos + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                applyOrdered(s, newlinePos, ordered.getNumber() + 1, ordered.getMargin(), ordered.getColor());
            }
            return;
        }

        // 4. 引用：空行退出；否则收窄 + 新行延续
        QuoteSpan quote = first(s, prev[0], scanEnd, QuoteSpan.class);
        if (quote != null) {
            s.removeSpan(quote);
            if (!prevBlank) {
                s.setSpan(new QuoteSpan(quote.getColor()), prev[0], newlinePos + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                int[] nl = ParagraphHelper.lineRange(s, newlinePos + 1);
                int end = Math.min(nl[1] + 1, s.length());
                s.setSpan(new QuoteSpan(quote.getColor()), nl[0], end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private void applyBullet(Editable s, int newlinePos, int margin, int radius, int color) {
        int[] nl = ParagraphHelper.lineRange(s, newlinePos + 1);
        int end = Math.min(nl[1] + 1, s.length());
        s.setSpan(new NoteBulletSpan(margin, radius, color), nl[0], end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void applyOrdered(Editable s, int newlinePos, int number, int margin, int color) {
        int[] nl = ParagraphHelper.lineRange(s, newlinePos + 1);
        int end = Math.min(nl[1] + 1, s.length());
        s.setSpan(new NoteOrderedSpan(number, margin, color), nl[0], end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static <T> T first(Spanned s, int start, int end, Class<T> cls) {
        T[] spans = s.getSpans(start, end, cls);
        return spans.length > 0 ? spans[0] : null;
    }
}
