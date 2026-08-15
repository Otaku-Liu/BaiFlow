package com.baiflow.android.editor;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * MarkdownParser / MarkdownEmitter 往返属性测试（纯 JVM，无需设备）。
 * <p>
 * 核心不变量：
 * 1. 受支持子集内 {@code emit(parse(md)) == canonical(md)}（往返稳定）；
 * 2. 幂等：{@code emit(parse(emit(parse(md)))) == emit(parse(md))}；
 * 3. 未知结构（表格/HTML/未闭合强调）文本原样透传，绝不丢文本；
 * 4. 代码块内容、标题标记、链接/图片/音频 URL 永不丢失。
 */
public class MarkdownRoundTripTest {

    private static String roundTrip(String md) {
        return MarkdownEmitter.emit(MarkdownParser.parse(md));
    }

    // ---- 基础往返 ----

    @Test
    public void roundTrip_plainText() {
        assertEquals("hello world", roundTrip("hello world"));
    }

    @Test
    public void roundTrip_headings() {
        assertEquals("# H1", roundTrip("# H1"));
        assertEquals("## H2", roundTrip("## H2"));
        assertEquals("### H3", roundTrip("### H3"));
        // 无空格不算标题，原样透传
        assertEquals("#NotHeading", roundTrip("#NotHeading"));
    }

    @Test
    public void roundTrip_boldItalicStrike() {
        assertEquals("**b** and *i* and ~~s~~", roundTrip("**b** and *i* and ~~s~~"));
    }

    @Test
    public void roundTrip_inlineCode() {
        assertEquals("`code` here", roundTrip("`code` here"));
    }

    @Test
    public void roundTrip_underline() {
        assertEquals("<u>under</u>", roundTrip("<u>under</u>"));
        assertEquals("<u>**bold**</u>", roundTrip("<u>**bold**</u>"));
        // 未闭合的 <u> 原样透传，不丢文本
        assertEquals("a <u> b", roundTrip("a <u> b"));
    }

    @Test
    public void roundTrip_linkAndImage() {
        assertEquals("[text](http://x.com)", roundTrip("[text](http://x.com)"));
        assertEquals("![alt](/api/notes/media/abc)", roundTrip("![alt](/api/notes/media/abc)"));
    }

    @Test
    public void roundTrip_audioLinkKeptAsLink() {
        // 音频引用是带 mediaType=audio 的普通链接，纯模型层不改写
        assertEquals("[录音](/api/notes/media/abc?mediaType=audio)",
                roundTrip("[录音](/api/notes/media/abc?mediaType=audio)"));
    }

    @Test
    public void roundTrip_lists() {
        assertEquals("- a\n- b\n1. x\n2. y", roundTrip("- a\n- b\n1. x\n2. y"));
    }

    @Test
    public void roundTrip_quote() {
        assertEquals("> line1\n> line2", roundTrip("> line1\n> line2"));
    }

    @Test
    public void roundTrip_codeBlockVerbatim() {
        String md = "```java\nSystem.out.println(\"*not bold* # still code\")\n```";
        assertEquals(md, roundTrip(md));
    }

    @Test
    public void roundTrip_blankLineBetweenBlocks() {
        assertEquals("para1\n\npara2", roundTrip("para1\n\npara2"));
    }

    @Test
    public void roundTrip_multiLineParagraphSoftBreak() {
        assertEquals("line1\nline2\n\nnext", roundTrip("line1\nline2\n\nnext"));
    }

    @Test
    public void roundTrip_nestedEmphasis() {
        assertEquals("**a *b* c**", roundTrip("**a *b* c**"));
        assertEquals("***both***", roundTrip("***both***"));
    }

    @Test
    public void roundTrip_headingWithNestedBold() {
        assertEquals("# Title **bold**", roundTrip("# Title **bold**"));
    }

    // ---- 未识别内容原样透传（防数据丢失）----

    @Test
    public void passThrough_table() {
        String md = "| a | b |\n|---|---|\n| 1 | 2 |";
        assertEquals(md, roundTrip(md));
    }

    @Test
    public void passThrough_html() {
        assertEquals("<div>\n</div>", roundTrip("<div>\n</div>"));
    }

    @Test
    public void passThrough_unclosedEmphasisAndArithmetic() {
        assertEquals("5 * 3 * 2", roundTrip("5 * 3 * 2"));
        assertEquals("**unclosed", roundTrip("**unclosed"));
        assertEquals("a ` b", roundTrip("a ` b"));
        assertEquals("C:\\path*star", roundTrip("C:\\path*star"));
    }

    @Test
    public void passThrough_underscoreEmphasisNotMangled() {
        // 下划线强调不支持，原样透传（不误改写为 *）
        assertEquals("_italic_", roundTrip("_italic_"));
    }

    @Test
    public void passThrough_referenceLinkSyntax() {
        assertEquals("[text][ref]\n[ref]: http://x.com", roundTrip("[text][ref]\n[ref]: http://x.com"));
    }

    // ---- 幂等性：对一批文档，二次往返与一次往返结果一致 ----

    private static final List<String> CORPUS = Arrays.asList(
            "# 标题\n\n正文段落 **加粗** 与 *斜体*。",
            "- 甲\n- 乙\n\n> 引用",
            "1. 一\n2. 二\n```python\nprint('x')\n```",
            "**a** *b* ~~c~~ <u>u</u> `d` [e](http://f) ![g](/api/notes/media/1)",
            "| h1 | h2 |\n|---|---|\n| 1 | 2 |\n\n## 标题\n",
            "[录音](/api/notes/media/9?mediaType=audio)\n\n# 记录",
            "第一行\n第二行\n\n> **引用**\n\n```\n裸围栏\n```"
    );

    @Test
    public void idempotence_acrossCorpus() {
        for (String md : CORPUS) {
            String once = roundTrip(md);
            String twice = roundTrip(once);
            assertEquals("幂等性失败: " + md, once, twice);
        }
    }

    // ---- 关键不丢保证 ----

    @Test
    public void neverLoseCodeBlockContent() {
        String md = "```\n# not a heading\n* not bold\n```\n\n后文";
        String out = roundTrip(md);
        assertTrue(out, out.contains("# not a heading"));
        assertTrue(out, out.contains("* not bold"));
        assertTrue(out, out.contains("后文"));
    }

    @Test
    public void neverLoseHeadingMarker() {
        assertEquals("# 标题", roundTrip("# 标题"));
    }

    @Test
    public void neverLoseMediaUrls() {
        String md = "![图](/api/notes/media/a1b2) 和 [录音](/api/notes/media/c3d4?mediaType=audio)";
        String out = roundTrip(md);
        assertTrue(out, out.contains("/api/notes/media/a1b2"));
        assertTrue(out, out.contains("/api/notes/media/c3d4?mediaType=audio"));
    }

    @Test
    public void adjacentMediaNoSpace_keepsImageAndAllAudio() {
        // 回归：相邻的 录音+图片+录音 中间无空格，旧 \S+ 贪婪会把 ![图] 和第二个 [录音] 吞进第一个链接 URL
        String md = "[录音](/api/notes/media/a1?mediaType=audio)![图](/api/notes/media/a2)[录音](/api/notes/media/a3?mediaType=audio)";
        String out = roundTrip(md);
        assertTrue(out, out.contains("/api/notes/media/a1?mediaType=audio"));
        assertTrue(out, out.contains("/api/notes/media/a2"));
        assertTrue(out, out.contains("/api/notes/media/a3?mediaType=audio"));
    }

    private static void assertTrue(String message, boolean cond) {
        org.junit.Assert.assertTrue(message, cond);
    }
}
