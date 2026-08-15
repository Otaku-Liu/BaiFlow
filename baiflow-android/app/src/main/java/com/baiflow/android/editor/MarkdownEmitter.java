package com.baiflow.android.editor;

import java.util.List;

/**
 * Doc → Markdown 发射器（纯 JVM）。
 * <p>
 * 只有被解析器识别成结构（块/行内）的内容才会被规范化重写；{@link DocModel.TextRun}
 * 与代码块内容一律原样透传，保证不丢文本。块间分隔：源中含空行则输出空行，否则单换行。
 */
public final class MarkdownEmitter {

    private MarkdownEmitter() {
    }

    /** 把文档模型序列化为 Markdown 源 */
    public static String emit(DocModel.Doc doc) {
        StringBuilder sb = new StringBuilder();
        boolean pendingBlank = false;
        for (DocModel.Block b : doc.blocks()) {
            if (b instanceof DocModel.BlankBlock) {
                pendingBlank = true;
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
                if (pendingBlank) {
                    sb.append('\n');
                }
            }
            pendingBlank = false;
            emitBlock(b, sb);
        }
        return sb.toString();
    }

    private static void emitBlock(DocModel.Block b, StringBuilder sb) {
        if (b instanceof DocModel.TextBlock tb) {
            sb.append(emitInlines(tb.inlines()));
        } else if (b instanceof DocModel.HeadingBlock h) {
            sb.append("#".repeat(h.level())).append(' ').append(emitInlines(h.inlines()));
        } else if (b instanceof DocModel.BulletListBlock bl) {
            appendItems(sb, "- ", bl.items());
        } else if (b instanceof DocModel.OrderedListBlock ol) {
            int n = 1;
            for (List<DocModel.Inline> item : ol.items()) {
                sb.append(n++).append(". ").append(emitInlines(item)).append('\n');
            }
            trimTrailingNewline(sb);
        } else if (b instanceof DocModel.QuoteBlock q) {
            String inl = emitInlines(q.inlines());
            String[] lines = inl.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append("> ").append(lines[i]);
            }
        } else if (b instanceof DocModel.CodeBlock cb) {
            emitCodeBlock(cb, sb);
        }
    }

    private static void appendItems(StringBuilder sb, String prefix, List<List<DocModel.Inline>> items) {
        for (List<DocModel.Inline> item : items) {
            sb.append(prefix).append(emitInlines(item)).append('\n');
        }
        trimTrailingNewline(sb);
    }

    private static void emitCodeBlock(DocModel.CodeBlock cb, StringBuilder sb) {
        String fence = fenceFor(cb.code());
        sb.append(fence);
        if (cb.language() != null && !cb.language().isEmpty()) {
            sb.append(cb.language());
        }
        sb.append('\n').append(cb.code());
        if (!cb.code().isEmpty() && !cb.code().endsWith("\n")) {
            sb.append('\n');
        }
        sb.append(fence);
    }

    private static void trimTrailingNewline(StringBuilder sb) {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
    }

    /** 行内 → markdown 源（块编辑器取文本块的原始 markdown 用） */
    public static String emitInlines(List<DocModel.Inline> inlines) {
        StringBuilder sb = new StringBuilder();
        for (DocModel.Inline in : inlines) {
            if (in instanceof DocModel.TextRun t) {
                sb.append(t.text());
            } else if (in instanceof DocModel.Bold b) {
                sb.append("**").append(emitInlines(b.children())).append("**");
            } else if (in instanceof DocModel.Italic i) {
                sb.append('*').append(emitInlines(i.children())).append('*');
            } else if (in instanceof DocModel.Strike st) {
                sb.append("~~").append(emitInlines(st.children())).append("~~");
            } else if (in instanceof DocModel.Underline u) {
                sb.append("<u>").append(emitInlines(u.children())).append("</u>");
            } else if (in instanceof DocModel.InlineCode c) {
                String d = backtickDelimiters(c.code());
                sb.append(d).append(c.code()).append(d);
            } else if (in instanceof DocModel.Link l) {
                sb.append('[').append(emitInlines(l.children())).append("](").append(l.url()).append(')');
            } else if (in instanceof DocModel.Image img) {
                sb.append("![").append(img.alt()).append("](").append(img.url()).append(')');
            }
        }
        return sb.toString();
    }

    /** 代码围栏：长度取内容中连续反引号最大长度 + 1，保证内容里的反引号不会误闭合围栏 */
    private static String fenceFor(String code) {
        int maxRun = 0;
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == '`') {
                int run = 1;
                while (i + run < code.length() && code.charAt(i + run) == '`') {
                    run++;
                }
                maxRun = Math.max(maxRun, run);
                i += run - 1;
            }
        }
        int len = Math.max(3, maxRun + 1);
        return "`".repeat(len);
    }

    /** 行内代码定界符：内容含反引号时用更长的反引号串包裹 */
    private static String backtickDelimiters(String code) {
        int maxRun = 0;
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == '`') {
                int run = 1;
                while (i + run < code.length() && code.charAt(i + run) == '`') {
                    run++;
                }
                maxRun = Math.max(maxRun, run);
                i += run - 1;
            }
        }
        return "`".repeat(maxRun + 1);
    }
}
