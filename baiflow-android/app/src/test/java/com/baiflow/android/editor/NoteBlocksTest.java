package com.baiflow.android.editor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * NoteBlocks（Doc ↔ 可编辑块）往返测试：Markdown → Doc → 块 → Doc → Markdown 应稳定。
 */
public class NoteBlocksTest {

    private static String roundTrip(String md) {
        DocModel.Doc doc = MarkdownParser.parse(md);
        java.util.List<NoteBlocks.Block> blocks = NoteBlocks.fromDoc(doc);
        return MarkdownEmitter.emit(NoteBlocks.toDoc(blocks));
    }

    @Test
    public void roundTrip_mixedBlocks() {
        String md = "# 标题\n\n正文第一段\n\n- 项目一\n- 项目二\n\n![画画](/api/notes/media/a1)\n\n[录音](/api/notes/media/b2?mediaType=audio&duration=1324)";
        String out = roundTrip(md);
        assertTrue(out, out.contains("# 标题"));
        assertTrue(out, out.contains("正文第一段"));
        assertTrue(out, out.contains("- 项目一"));
        assertTrue(out, out.contains("![画画](/api/notes/media/a1)"));
        assertTrue(out, out.contains("[录音](/api/notes/media/b2?mediaType=audio&duration=1324)"));
    }

    @Test
    public void mediaBlockUrlsPreserved() {
        DocModel.Doc doc = MarkdownParser.parse("![图](/api/notes/media/a1)[录音](/api/notes/media/b2?mediaType=audio)");
        java.util.List<NoteBlocks.Block> blocks = NoteBlocks.fromDoc(doc);
        // 相邻无空格的图片+音频：应识别出 1 个图片块 + 1 个音频块
        boolean hasImage = blocks.stream().anyMatch(b -> b.type == NoteBlocks.IMAGE && "/api/notes/media/a1".equals(b.mediaUrl));
        boolean hasAudio = blocks.stream().anyMatch(b -> b.type == NoteBlocks.AUDIO && b.mediaUrl.contains("/api/notes/media/b2"));
        assertTrue("image block missing", hasImage);
        assertTrue("audio block missing", hasAudio);
    }

    @Test
    public void textBlockRawMarkdownPassThrough() {
        assertEquals("**加粗**和*斜体*",
                roundTrip("**加粗**和*斜体*"));
    }

    @Test
    public void blankLinesAreSeparatorsNotBlocks() {
        String md = "para1\n\npara2";
        java.util.List<NoteBlocks.Block> blocks = NoteBlocks.fromDoc(MarkdownParser.parse(md));
        // 空行不再生成可见空块
        assertEquals(2, blocks.size());
        assertEquals(NoteBlocks.TEXT, blocks.get(0).type);
        assertEquals(NoteBlocks.TEXT, blocks.get(1).type);
        // 往返仍保留空行分隔（\n\n）
        assertEquals("para1\n\npara2", roundTrip(md));
    }
}
