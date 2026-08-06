package com.baiflow.android.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 透传式 Markdown 解析器（String → Doc，纯 JVM）。
 * <p>
 * 只识别受支持的块/行内语法（标题、列表、引用、围栏代码、加粗、斜体、删除线、
 * 行内代码、链接、图片）；识别不了的字符一律归入 {@link DocModel.TextRun} 原样
 * 透传，序列化时不变——从而保证从 Web 端（Vditor）写入的任意 Markdown 不丢数据。
 */
public final class MarkdownParser {

    // ---- 块级 ----
    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^[-*+]\\s+(.*)$");
    private static final Pattern ORDERED = Pattern.compile("^\\d{1,9}[.)]\\s+(.*)$");
    private static final Pattern QUOTE = Pattern.compile("^>\\s?(.*)$");
    private static final Pattern FENCE = Pattern.compile("^(`{3,}|~{3,})(.*)$");

    // ---- 行内（按顺序优先匹配：图片 > 链接 > 行内代码 > 粗斜体 > 加粗 > 删除线 > 斜体）----
    private static final Pattern IMAGE = Pattern.compile("^!\\[([^]]*)]\\((\\S+)\\)");
    private static final Pattern LINK = Pattern.compile("^\\[([^]]*)]\\((\\S+)\\)");
    private static final Pattern INLINE_CODE = Pattern.compile("^(\\`{1,})(.*?)\\1");
    private static final Pattern BOLD_ITALIC = Pattern.compile("^\\*\\*\\*(.+?)\\*\\*\\*");
    private static final Pattern BOLD = Pattern.compile("^\\*\\*(.+?)\\*\\*");
    private static final Pattern STRIKE = Pattern.compile("^~~(.+?)~~");
    private static final Pattern ITALIC = Pattern.compile("^\\*(.+?)\\*");

    private MarkdownParser() {
    }

    /** 解析整篇 Markdown 源为文档模型 */
    public static DocModel.Doc parse(String source) {
        if (source == null) {
            source = "";
        }
        String[] lines = source.split("\n", -1);
        List<DocModel.Block> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];

            // 围栏代码块
            Matcher fm = FENCE.matcher(line);
            if (fm.matches()) {
                String fence = fm.group(1);
                String lang = fm.group(2).trim();
                StringBuilder code = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < lines.length) {
                    if (isClosingFence(lines[i], fence)) {
                        closed = true;
                        i++;
                        break;
                    }
                    code.append(lines[i]).append('\n');
                    i++;
                }
                String codeStr = code.toString();
                if (codeStr.endsWith("\n")) {
                    codeStr = codeStr.substring(0, codeStr.length() - 1);
                }
                blocks.add(new DocModel.CodeBlock(lang, codeStr));
                if (!closed) {
                    break; // 未闭合围栏：剩余内容整体视为代码，原样保留
                }
                continue;
            }

            // 空行
            if (line.isBlank()) {
                blocks.add(new DocModel.BlankBlock());
                i++;
                continue;
            }

            // 标题
            Matcher hm = HEADING.matcher(line);
            if (hm.matches()) {
                blocks.add(new DocModel.HeadingBlock(hm.group(1).length(), parseInlines(hm.group(2))));
                i++;
                continue;
            }

            // 引用：每行一个引用块（编辑器为单行段落模型，逐行加 > 前缀）
            Matcher qm = QUOTE.matcher(line);
            if (qm.matches()) {
                blocks.add(new DocModel.QuoteBlock(parseInlines(qm.group(1))));
                i++;
                continue;
            }

            // 无序列表：连续项合并为一块
            if (BULLET.matcher(line).matches()) {
                List<List<DocModel.Inline>> items = new ArrayList<>();
                while (i < lines.length) {
                    Matcher m = BULLET.matcher(lines[i]);
                    if (m.matches()) {
                        items.add(parseInlines(m.group(1)));
                        i++;
                    } else {
                        break;
                    }
                }
                blocks.add(new DocModel.BulletListBlock(items));
                continue;
            }

            // 有序列表
            if (ORDERED.matcher(line).matches()) {
                List<List<DocModel.Inline>> items = new ArrayList<>();
                while (i < lines.length) {
                    Matcher m = ORDERED.matcher(lines[i]);
                    if (m.matches()) {
                        items.add(parseInlines(m.group(1)));
                        i++;
                    } else {
                        break;
                    }
                }
                blocks.add(new DocModel.OrderedListBlock(items));
                continue;
            }

            // 普通段落：连续非特殊行合并（保持软换行）
            List<String> pLines = new ArrayList<>();
            while (i < lines.length) {
                String l = lines[i];
                if (l.isBlank() || HEADING.matcher(l).matches() || BULLET.matcher(l).matches()
                        || ORDERED.matcher(l).matches() || QUOTE.matcher(l).matches()
                        || FENCE.matcher(l).matches()) {
                    break;
                }
                pLines.add(l);
                i++;
            }
            if (!pLines.isEmpty()) {
                blocks.add(new DocModel.TextBlock(parseInlines(String.join("\n", pLines))));
            } else {
                // 防御：确保循环推进
                blocks.add(new DocModel.TextBlock(List.of(new DocModel.TextRun(line))));
                i++;
            }
        }
        return new DocModel.Doc(blocks);
    }

    /** 行内解析：从左到右扫描，只重写完整识别的 token，其余字符进 TextRun */
    static List<DocModel.Inline> parseInlines(String s) {
        List<DocModel.Inline> out = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            Matcher m;
            String rest = s.substring(i);

            if ((m = IMAGE.matcher(rest)).find()) {
                flush(text, out);
                out.add(new DocModel.Image(m.group(1), m.group(2)));
                i += m.end();
                continue;
            }
            if ((m = LINK.matcher(rest)).find()) {
                flush(text, out);
                out.add(new DocModel.Link(m.group(2), parseInlines(m.group(1))));
                i += m.end();
                continue;
            }
            if ((m = INLINE_CODE.matcher(rest)).find()) {
                String content = m.group(2);
                if (!content.isEmpty()) {
                    flush(text, out);
                    out.add(new DocModel.InlineCode(content));
                    i += m.end();
                    continue;
                }
            }
            if ((m = BOLD_ITALIC.matcher(rest)).find() && validEmphasis(m.group(1))) {
                flush(text, out);
                out.add(new DocModel.Bold(List.of(new DocModel.Italic(parseInlines(m.group(1))))));
                i += m.end();
                continue;
            }
            if ((m = BOLD.matcher(rest)).find() && validEmphasis(m.group(1))) {
                flush(text, out);
                out.add(new DocModel.Bold(parseInlines(m.group(1))));
                i += m.end();
                continue;
            }
            if ((m = STRIKE.matcher(rest)).find() && validEmphasis(m.group(1))) {
                flush(text, out);
                out.add(new DocModel.Strike(parseInlines(m.group(1))));
                i += m.end();
                continue;
            }
            if ((m = ITALIC.matcher(rest)).find() && validEmphasis(m.group(1))) {
                flush(text, out);
                out.add(new DocModel.Italic(parseInlines(m.group(1))));
                i += m.end();
                continue;
            }

            char c = s.charAt(i);
            // 反斜杠仅转义 ASCII 标点（CommonMark 规则）；非转义字符前的反斜杠按字面保留，
            // 避免把 "C:\path" 这类路径中的反斜杠吞掉
            if (c == '\\' && i + 1 < s.length() && isEscapable(s.charAt(i + 1))) {
                text.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            text.append(c);
            i++;
        }
        flush(text, out);
        return out;
    }

    private static void flush(StringBuilder text, List<DocModel.Inline> out) {
        if (text.length() > 0) {
            out.add(new DocModel.TextRun(text.toString()));
            text.setLength(0);
        }
    }

    /** 反斜杠可转义的 ASCII 标点集合（对齐 CommonMark 的 escapable punctuation） */
    private static boolean isEscapable(char c) {
        return "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".indexOf(c) >= 0;
    }

    /** 强调内容两端不能是空白（对齐 CommonMark，避免把 "5 * 3" 误判为斜体） */
    private static boolean validEmphasis(String content) {
        if (content.isEmpty()) {
            return false;
        }
        return !Character.isWhitespace(content.charAt(0))
                && !Character.isWhitespace(content.charAt(content.length() - 1));
    }

    /** 围栏闭合行：trim 后全部由与开围栏相同的字符组成且长度 ≥ 3 */
    private static boolean isClosingFence(String line, String fence) {
        String t = line.trim();
        if (t.length() < 3) {
            return false;
        }
        char c = fence.charAt(0);
        for (int k = 0; k < t.length(); k++) {
            if (t.charAt(k) != c) {
                return false;
            }
        }
        return true;
    }
}
