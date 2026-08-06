package com.baiflow.android.editor;

import android.text.SpannableStringBuilder;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 适配层往返测试（Robolectric）：{@code parse → toSpannable → extract → emit}，
 * 验证编辑器 Spannable 状态与 Markdown 双向转换的一致性与不丢数据。
 */
@RunWith(RobolectricTestRunner.class)
public class EditorRoundTripTest {

    private EditorStyle style;

    @Before
    public void setUp() {
        style = new EditorStyle(ApplicationProvider.getApplicationContext());
    }

    private static String throughSpannable(String md, EditorStyle s) {
        DocModel.Doc doc = MarkdownParser.parse(md);
        SpannableStringBuilder sb = ModelToSpanned.toSpannable(doc, s);
        DocModel.Doc back = SpanExtractor.extract(sb);
        return MarkdownEmitter.emit(back);
    }

    @Test
    public void roundTrip_supportedSubset() {
        assertEquals("hello", throughSpannable("hello", style));
        assertEquals("# Title", throughSpannable("# Title", style));
        assertEquals("## Title **bold**", throughSpannable("## Title **bold**", style));
        assertEquals("**b** and *i* and ~~s~~", throughSpannable("**b** and *i* and ~~s~~", style));
        assertEquals("`code`", throughSpannable("`code`", style));
        assertEquals("[text](http://x.com)", throughSpannable("[text](http://x.com)", style));
    }

    @Test
    public void roundTrip_lists() {
        assertEquals("- a\n- b\n1. x\n2. y", throughSpannable("- a\n- b\n1. x\n2. y", style));
    }

    @Test
    public void roundTrip_quote() {
        assertEquals("> line1\n> line2", throughSpannable("> line1\n> line2", style));
    }

    @Test
    public void roundTrip_codeBlock() {
        String md = "```java\nSystem.out.println(\"*x* # y\")\n```";
        assertEquals(md, throughSpannable(md, style));
    }

    @Test
    public void roundTrip_media() {
        assertEquals("![图](/api/notes/media/abc)",
                throughSpannable("![图](/api/notes/media/abc)", style));
        assertEquals("[录音](/api/notes/media/abc?mediaType=audio)",
                throughSpannable("[录音](/api/notes/media/abc?mediaType=audio)", style));
    }

    @Test
    public void roundTrip_blankLinesAndSoftBreaks() {
        assertEquals("para1\n\npara2", throughSpannable("para1\n\npara2", style));
        assertEquals("l1\nl2\n\nnext", throughSpannable("l1\nl2\n\nnext", style));
    }

    @Test
    public void roundTrip_unknownContentPreserved() {
        assertEquals("| a | b |\n|---|---|\n| 1 | 2 |",
                throughSpannable("| a | b |\n|---|---|\n| 1 | 2 |", style));
        assertEquals("5 * 3 * 2", throughSpannable("5 * 3 * 2", style));
    }

    @Test
    public void noDataLoss_acrossCorpus() {
        List<String> corpus = Arrays.asList(
                "# 标题\n\n正文 **加粗** *斜体*",
                "- 甲\n- 乙\n\n> 引用",
                "1. 一\n2. 二\n```python\nprint('x')\n```",
                "**a** *b* ~~c~~ `d` [e](http://f) ![g](/api/notes/media/1)",
                "[录音](/api/notes/media/9?mediaType=audio)\n\n# 记录",
                "第一行\n第二行\n\n> 引用"
        );
        for (String md : corpus) {
            String once = throughSpannable(md, style);
            String twice = throughSpannable(once, style);
            assertEquals("不稳定: " + md, once, twice);
            // 文本不丢失：所有字母/数字/CJK 连续 token 必须完整出现在输出里
            for (String token : tokens(md)) {
                assertTrue("缺 token [" + token + "] 于: " + md + "\n输出: " + once, once.contains(token));
            }
        }
    }

    /** 提取连续字母/数字/CJK token（≥2 字符），用于不丢失校验 */
    private static java.util.List<String> tokens(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                cur.append(c);
            } else {
                if (cur.length() >= 2) {
                    out.add(cur.toString());
                }
                cur.setLength(0);
            }
        }
        if (cur.length() >= 2) {
            out.add(cur.toString());
        }
        return out;
    }
}
