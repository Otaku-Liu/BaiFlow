package com.baiflow.android.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 笔记冲突块级差异计算（纯逻辑，无 UI/资源依赖）。
 * <p>
 * 对比本地未保存内容与服务端内容的块（经 {@link MarkdownParser}/{@link NoteBlocks} 解析），
 * 返回双方各自独有的块预览列表，供冲突弹窗展示；Web 端用 {@code markdownToBlocks} 实现同等的块级差异。
 */
public final class NoteDiff {

    private NoteDiff() {
    }

    /** 差异结果：本地独有 / 服务端独有的块预览（单行、截断 ≤40 字符） */
    public record DiffResult(List<String> localOnly, List<String> serverOnly) {
    }

    /** 计算双方改动块；相同块不计入（两端只改不同块时各自可见自己的改动） */
    public static DiffResult diff(String localContent, String serverContent) {
        List<NoteBlocks.Block> local = NoteBlocks.fromDoc(MarkdownParser.parse(localContent));
        List<NoteBlocks.Block> server = NoteBlocks.fromDoc(MarkdownParser.parse(serverContent));
        Set<String> localKeys = new HashSet<>();
        Set<String> serverKeys = new HashSet<>();
        for (NoteBlocks.Block b : local) {
            localKeys.add(blockKey(b));
        }
        for (NoteBlocks.Block b : server) {
            serverKeys.add(blockKey(b));
        }
        List<String> localOnly = new ArrayList<>();
        List<String> serverOnly = new ArrayList<>();
        for (NoteBlocks.Block b : local) {
            if (!serverKeys.contains(blockKey(b))) {
                localOnly.add(blockPreview(b));
            }
        }
        for (NoteBlocks.Block b : server) {
            if (!localKeys.contains(blockKey(b))) {
                serverOnly.add(blockPreview(b));
            }
        }
        return new DiffResult(localOnly, serverOnly);
    }

    /** 块唯一键（文本/标题级别/列表项/媒体 URL 参与比对） */
    private static String blockKey(NoteBlocks.Block b) {
        switch (b.type) {
            case NoteBlocks.HEADING:
                return "H" + b.level + " " + b.text;
            case NoteBlocks.BULLET:
            case NoteBlocks.ORDERED:
                StringBuilder sb = new StringBuilder();
                for (String it : b.items) {
                    sb.append(it).append('\n');
                }
                return sb.toString();
            case NoteBlocks.CODE:
                return "CODE\n" + b.language + "\n" + b.text;
            case NoteBlocks.IMAGE:
                return "IMG " + b.mediaUrl + " " + (b.alt != null ? b.alt : "");
            case NoteBlocks.AUDIO:
                return "AUDIO " + b.mediaUrl;
            default:
                return b.text;
        }
    }

    /** 块预览文本（单行 + 截断，防弹窗过长） */
    private static String blockPreview(NoteBlocks.Block b) {
        String s = blockKey(b);
        if (s == null) {
            return "";
        }
        s = s.replace('\n', ' ');
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
