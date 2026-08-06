package com.baiflow.android.editor;

import android.text.SpannableStringBuilder;
import android.text.Spanned;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

/**
 * 回车延续/退出列表行为测试（Robolectric，直接驱动 TextWatcher 回调序列）。
 * <p>
 * 模拟软键盘 commitText('\n') 触发 {@code beforeTextChanged → insert → afterTextChanged}。
 */
@RunWith(RobolectricTestRunner.class)
public class ListKeyListenerTest {

    @Test
    public void enterOnNonEmptyBullet_continuesList() {
        SpannableStringBuilder sb = new SpannableStringBuilder("甲\n");
        sb.setSpan(new NoteBulletSpan(20, 3, 0xFF000000), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ListKeyListener l = new ListKeyListener();
        int at = 1; // 光标在「甲」后
        l.beforeTextChanged(sb, at, 0, 1);
        sb.insert(at, "\n");
        l.afterTextChanged(sb);

        NoteBulletSpan[] spans = sb.getSpans(0, sb.length(), NoteBulletSpan.class);
        assertEquals("回车后应有两条 bullet span", 2, spans.length);
        // 第一行 [0,2) 覆盖 "甲\n"，第二行 [2,3) 覆盖空行 "\n"
        int s0 = sb.getSpanStart(spans[0]);
        int s1 = sb.getSpanStart(spans[1]);
        assertEquals(0, s0);
        assertEquals(2, s1);
        assertEquals("甲\n\n", sb.toString());
    }

    @Test
    public void enterOnEmptyBullet_exitsList() {
        SpannableStringBuilder sb = new SpannableStringBuilder("\n");
        sb.setSpan(new NoteBulletSpan(20, 3, 0xFF000000), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ListKeyListener l = new ListKeyListener();
        l.beforeTextChanged(sb, 0, 0, 1);
        sb.insert(0, "\n");
        l.afterTextChanged(sb);

        NoteBulletSpan[] spans = sb.getSpans(0, sb.length(), NoteBulletSpan.class);
        assertEquals("空项回车应退出列表", 0, spans.length);
    }

    @Test
    public void enterOnHeading_doesNotContinue() {
        SpannableStringBuilder sb = new SpannableStringBuilder("标题\n");
        sb.setSpan(new NoteHeadingSpan(1, 0xFF000000), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ListKeyListener l = new ListKeyListener();
        l.beforeTextChanged(sb, 2, 0, 1);
        sb.insert(2, "\n");
        l.afterTextChanged(sb);

        assertEquals("标题\n\n", sb.toString());
        NoteHeadingSpan[] spans = sb.getSpans(0, sb.length(), NoteHeadingSpan.class);
        assertEquals("标题回车后仍只有一行标题", 1, spans.length);
        assertEquals("标题span应收窄到第一行", 0, sb.getSpanStart(spans[0]));
        assertEquals(3, sb.getSpanEnd(spans[0]));
    }
}
