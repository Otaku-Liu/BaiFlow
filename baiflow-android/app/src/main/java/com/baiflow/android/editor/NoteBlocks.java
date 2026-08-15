package com.baiflow.android.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * 可编辑块模型 + {@link DocModel.Doc} ↔ 块 转换（块编辑器用，纯 JVM）。
 * <p>
 * 块类型：文本/标题/无序/有序/引用/代码/图片/音频/空行。文本类块的 text 存的是
 * 「行内 markdown 源」（编辑时以原始 markdown 显示，保存时透传），与 Web 端一致；
 * 图片/音频是独立块（真实组件），由整行媒体段落识别而来。
 */
public final class NoteBlocks {

    public static final int TEXT = 0;
    public static final int HEADING = 1;
    public static final int BULLET = 2;
    public static final int ORDERED = 3;
    public static final int CODE = 5;
    public static final int IMAGE = 6;
    public static final int AUDIO = 7;

    /** 可编辑块 */
    public static class Block {
        public int type = TEXT;
        public int level = 1;                        // HEADING 1-3
        public String text = "";                     // TEXT/HEADING/QUOTE 行内 md 源；CODE 代码
        public List<String> items = new ArrayList<>();  // BULLET/ORDERED 项（每项一行 md 源）
        public String mediaUrl = "";                 // IMAGE/AUDIO
        public String alt = "";                      // IMAGE
        public long duration;                        // AUDIO 时长 ms
        public String language = "";                 // CODE
    }

    private NoteBlocks() {
    }

    /** Doc → 可编辑块列表（单个图片/音频行内 的段落识别为媒体块） */
    public static List<Block> fromDoc(DocModel.Doc doc) {
        List<Block> out = new ArrayList<>();
        for (DocModel.Block b : doc.blocks()) {
            if (b instanceof DocModel.BlankBlock) {
                // 空行是块间分隔，不生成空块（与 Web 一致：空行仅作分隔，不产生可见卡片）
                continue;
            } else if (b instanceof DocModel.TextBlock tb) {
                // 把行内中的图片/音频拆成独立媒体块，其余文本聚合为一个文本块（行内 markdown 源透传）
                List<DocModel.Inline> run = new ArrayList<>();
                for (DocModel.Inline it : tb.inlines()) {
                    if (it instanceof DocModel.Image img) {
                        flushText(out, run);
                        Block bl = newBlock(IMAGE);
                        bl.mediaUrl = img.url();
                        bl.alt = img.alt() != null ? img.alt() : "";
                        out.add(bl);
                    } else if (it instanceof DocModel.Link link && isAudio(link.url())) {
                        flushText(out, run);
                        Block bl = newBlock(AUDIO);
                        bl.mediaUrl = link.url();
                        bl.duration = audioDuration(link.url());
                        out.add(bl);
                    } else {
                        run.add(it);
                    }
                }
                flushText(out, run);
            } else if (b instanceof DocModel.HeadingBlock h) {
                Block bl = newBlock(HEADING);
                bl.level = h.level();
                bl.text = MarkdownEmitter.emitInlines(h.inlines());
                out.add(bl);
            } else if (b instanceof DocModel.BulletListBlock blk) {
                // 列表块已移除：重建为「- item」原始 markdown 的文本块
                Block bl = newBlock(TEXT);
                StringBuilder sb = new StringBuilder();
                for (List<DocModel.Inline> item : blk.items()) {
                    sb.append("- ").append(MarkdownEmitter.emitInlines(item)).append('\n');
                }
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                }
                bl.text = sb.toString();
                out.add(bl);
            } else if (b instanceof DocModel.OrderedListBlock ol) {
                Block bl = newBlock(TEXT);
                StringBuilder sb = new StringBuilder();
                int n = 1;
                for (List<DocModel.Inline> item : ol.items()) {
                    sb.append(n++).append(". ").append(MarkdownEmitter.emitInlines(item)).append('\n');
                }
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                }
                bl.text = sb.toString();
                out.add(bl);
            } else if (b instanceof DocModel.QuoteBlock q) {
                // 引用块功能已移除：旧引用内容映射为普通文本块（保留内容，丢弃 > 前缀）
                Block bl = newBlock(TEXT);
                bl.text = MarkdownEmitter.emitInlines(q.inlines());
                out.add(bl);
            } else if (b instanceof DocModel.CodeBlock cb) {
                // 代码块功能已移除：解析出的围栏代码映射为普通文本块，保留围栏原始 markdown
                Block bl = newBlock(TEXT);
                String lang = cb.language() != null ? cb.language() : "";
                bl.text = "```" + lang + "\n" + (cb.code() != null ? cb.code() : "") + "\n```";
                out.add(bl);
            }
        }
        return out;
    }

    /** 可编辑块列表 → Doc（媒体块还原为单行内段落，保证 Markdown 往返一致） */
    public static DocModel.Doc toDoc(List<Block> blocks) {
        List<DocModel.Block> out = new ArrayList<>();
        boolean first = true;
        for (Block b : blocks) {
            if (!first) {
                // 块间以空行分隔（与 Web blocksToMarkdown 的 \n\n 连接一致）
                out.add(new DocModel.BlankBlock());
            }
            first = false;
            switch (b.type) {
                case TEXT:
                    out.add(new DocModel.TextBlock(List.of(new DocModel.TextRun(b.text))));
                    break;
                case HEADING:
                    out.add(new DocModel.HeadingBlock(Math.max(1, Math.min(3, b.level)),
                            List.of(new DocModel.TextRun(b.text))));
                    break;
                case BULLET:
                    out.add(new DocModel.BulletListBlock(toItems(b.items)));
                    break;
                case ORDERED:
                    out.add(new DocModel.OrderedListBlock(toItems(b.items)));
                    break;
                case CODE:
                    out.add(new DocModel.CodeBlock(b.language, b.text));
                    break;
                case IMAGE:
                    out.add(new DocModel.TextBlock(List.of(new DocModel.Image(b.alt, b.mediaUrl))));
                    break;
                case AUDIO:
                    out.add(new DocModel.TextBlock(List.of(
                            new DocModel.Link(b.mediaUrl, List.of(new DocModel.TextRun("录音"))))));
                    break;
                default:
                    break;
            }
        }
        return new DocModel.Doc(out);
    }

    private static Block newBlock(int type) {
        Block b = new Block();
        b.type = type;
        return b;
    }

    /** 累积的文本行内 → 文本块 */
    private static void flushText(List<Block> out, List<DocModel.Inline> run) {
        if (run.isEmpty()) {
            return;
        }
        Block bl = newBlock(TEXT);
        bl.text = MarkdownEmitter.emitInlines(run);
        out.add(bl);
        run.clear();
    }

    private static List<List<DocModel.Inline>> toItems(List<String> items) {
        List<List<DocModel.Inline>> out = new ArrayList<>();
        for (String s : items) {
            out.add(List.of(new DocModel.TextRun(s)));
        }
        return out;
    }

    private static boolean isAudio(String url) {
        return url != null && url.contains("mediaType=audio");
    }

    /** 从 &duration=ms 读时长 */
    private static long audioDuration(String url) {
        int q = url != null ? url.indexOf("duration=") : -1;
        if (q < 0) {
            return 0;
        }
        int end = url.indexOf('&', q);
        String v = end < 0 ? url.substring(q + "duration=".length())
                : url.substring(q + "duration=".length(), end);
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
