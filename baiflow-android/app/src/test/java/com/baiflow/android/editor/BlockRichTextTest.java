package com.baiflow.android.editor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * 块级所见即所得往返测试（Robolectric）：markdown → {@link BlockRichText#toSpannable}
 * → {@link BlockRichText#toMarkdown} 应为原样（支持子集内往返稳定），且幂等。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BlockRichTextTest {

    private final EditorStyle style =
            new EditorStyle(ApplicationProvider.getApplicationContext());

    private String round(String md) {
        return BlockRichText.toMarkdown(BlockRichText.toSpannable(md, style));
    }

    @Test
    public void plainText() {
        assertEquals("hello world", round("hello world"));
    }

    @Test
    public void boldItalicStrikeUnderline() {
        assertEquals("**bold**", round("**bold**"));
        assertEquals("*italic*", round("*italic*"));
        assertEquals("~~strike~~", round("~~strike~~"));
        assertEquals("<u>under</u>", round("<u>under</u>"));
    }

    @Test
    public void mixedInline() {
        assertEquals("**b** and *i* and <u>u</u> and ~~s~~",
                round("**b** and *i* and <u>u</u> and ~~s~~"));
    }

    @Test
    public void inlineCode() {
        assertEquals("`code` here", round("`code` here"));
    }

    @Test
    public void link() {
        assertEquals("[text](http://x.com)", round("[text](http://x.com)"));
    }

    @Test
    public void nestedUnderlineBold() {
        assertEquals("<u>**both**</u>", round("<u>**both**</u>"));
    }

    @Test
    public void boldItalicBoth() {
        assertEquals("***both***", round("***both***"));
    }

    @Test
    public void passthroughPlainMd() {
        // 未识别的行内内容原样透传，不丢文本
        assertEquals("5 * 3 * 2", round("5 * 3 * 2"));
        assertEquals("C:\\path", round("C:\\path"));
        assertEquals("_underscore_", round("_underscore_"));
    }

    @Test
    public void newlineSoftBreak() {
        assertEquals("line1\nline2", round("line1\nline2"));
    }

    @Test
    public void idempotentAcrossCorpus() {
        List<String> corpus = Arrays.asList(
                "**b** and *i* and <u>u</u> and ~~s~~ and `c`",
                "plain text with [link](http://x)",
                "5 * 3 * 2",
                "<u>**both**</u>",
                "line1\nline2",
                "**a** *b* <u>c</u> ~~d~~ `e` [f](http://g)"
        );
        for (String md : corpus) {
            String once = round(md);
            String twice = round(once);
            assertEquals("幂等失败: " + md, once, twice);
        }
    }
}
