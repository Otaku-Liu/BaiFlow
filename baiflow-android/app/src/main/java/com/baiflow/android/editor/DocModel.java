package com.baiflow.android.editor;

import java.util.List;

/**
 * 富文本笔记的纯 JVM 文档模型（无 android.* 依赖，可在 JVM 单测直接跑）。
 * <p>
 * 作为 Markdown ↔ Spannable 往返的中间层：{@link MarkdownParser} 产出 Doc，
 * {@link MarkdownEmitter} 把 Doc 还原为 Markdown。未知的块/行内内容一律以
 * {@link TextRun} / 纯文本透传，保证任何文本都不丢失（Web 端 Vditor 写入的
 * 表格/HTML 等未识别结构也能原样回写）。
 */
public final class DocModel {

    private DocModel() {
    }

    /** 整篇文档 = 块序列 */
    public record Doc(List<Block> blocks) {
    }

    /** 块 */
    public sealed interface Block permits TextBlock, HeadingBlock, BulletListBlock,
            OrderedListBlock, QuoteBlock, CodeBlock, BlankBlock {
    }

    /** 普通段落（行内可含格式）；同一段落的多行以 {@code \n} 连接，保持软换行 */
    public record TextBlock(List<Inline> inlines) implements Block {
    }

    /** ATX 标题（1~3 级） */
    public record HeadingBlock(int level, List<Inline> inlines) implements Block {
    }

    /** 无序列表项（每项一行） */
    public record BulletListBlock(List<List<Inline>> items) implements Block {
    }

    /** 有序列表项（序号在序列化时从 1 重算，不依赖存储值） */
    public record OrderedListBlock(List<List<Inline>> items) implements Block {
    }

    /** 引用块（多行以 {@code \n} 连接，序列化时每行加 {@code > }） */
    public record QuoteBlock(List<Inline> inlines) implements Block {
    }

    /** 围栏代码块（内容原样透传，绝不走行内发射器） */
    public record CodeBlock(String language, String code) implements Block {
    }

    /** 空行（块间分隔标记，序列化为空） */
    public record BlankBlock() implements Block {
    }

    /** 行内 */
    public sealed interface Inline permits TextRun, Bold, Italic, Strike, InlineCode, Link, Image {
    }

    /** 纯文本（未经识别的字符一律归此，原样透传） */
    public record TextRun(String text) implements Inline {
    }

    /** 加粗 */
    public record Bold(List<Inline> children) implements Inline {
    }

    /** 斜体 */
    public record Italic(List<Inline> children) implements Inline {
    }

    /** 删除线 */
    public record Strike(List<Inline> children) implements Inline {
    }

    /** 行内代码 */
    public record InlineCode(String code) implements Inline {
    }

    /**
     * 链接。音频引用同样建模为链接：url 携带 {@code mediaType=audio} 查询参数，
     * 由 Android 适配层在渲染时识别成可播放的音频 span。
     */
    public record Link(String url, List<Inline> children) implements Inline {
    }

    /** 图片 */
    public record Image(String alt, String url) implements Inline {
    }
}
