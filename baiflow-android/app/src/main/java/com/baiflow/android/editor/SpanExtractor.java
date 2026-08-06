package com.baiflow.android.editor;

import android.graphics.Typeface;
import android.text.Spanned;
import android.text.style.QuoteSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * SpannableStringBuilder → Doc（编辑器状态还原为文档模型）。
 * <p>
 * 以段落 span（{@link NoteParagraphMarker} + {@link QuoteSpan}）为边界切分文本，
 * 段落格式映射到对应块，未覆盖的区间按行重组为普通段落/空行。
 * 行内格式用「边界切分 + 固定优先级链式包裹」近似嵌套（重叠 span 摊平为相邻节点，
 * 文本不丢失，属 v1 接受的取舍）。
 */
public final class SpanExtractor {

    private SpanExtractor() {
    }

    public static DocModel.Doc extract(Spanned s) {
        List<Para> paras = new ArrayList<>();
        NoteParagraphMarker[] markers = s.getSpans(0, s.length(), NoteParagraphMarker.class);
        for (NoteParagraphMarker m : markers) {
            paras.add(new Para(m, s.getSpanStart(m), s.getSpanEnd(m)));
        }
        QuoteSpan[] quotes = s.getSpans(0, s.length(), QuoteSpan.class);
        for (QuoteSpan q : quotes) {
            paras.add(new Para(q, s.getSpanStart(q), s.getSpanEnd(q)));
        }
        paras.sort(Comparator.comparingInt(p -> p.start));

        List<DocModel.Block> blocks = new ArrayList<>();
        List<List<DocModel.Inline>> pendingBullets = new ArrayList<>();
        List<List<DocModel.Inline>> pendingOrdered = new ArrayList<>();
        List<DocModel.Inline> pendingQuote = null;

        int p = 0;
        for (Para para : paras) {
            if (para.start < p) {
                continue; // 重叠防御：跳过已被覆盖的段落
            }
            if (para.start > p) {
                flushLists(blocks, pendingBullets, pendingOrdered, pendingQuote);
                pendingQuote = null;
                addPlainGap(s, p, para.start, blocks);
                p = para.start;
            }
            int contentEnd = para.end;
            if (contentEnd > p && s.charAt(contentEnd - 1) == '\n') {
                contentEnd--;
            }

            if (para.span instanceof NoteHeadingSpan h) {
                flushLists(blocks, pendingBullets, pendingOrdered, pendingQuote);
                pendingQuote = null;
                blocks.add(new DocModel.HeadingBlock(h.getLevel(), extractInlines(s, p, contentEnd)));
            } else if (para.span instanceof NoteBulletSpan) {
                pendingBullets.add(extractInlines(s, p, contentEnd));
            } else if (para.span instanceof NoteOrderedSpan) {
                pendingOrdered.add(extractInlines(s, p, contentEnd));
            } else if (para.span instanceof NoteCodeBlockSpan cb) {
                flushLists(blocks, pendingBullets, pendingOrdered, pendingQuote);
                pendingQuote = null;
                blocks.add(new DocModel.CodeBlock(cb.getLanguage(), s.subSequence(p, contentEnd).toString()));
            } else if (para.span instanceof QuoteSpan) {
                flushLists(blocks, pendingBullets, pendingOrdered, pendingQuote);
                pendingQuote = extractInlines(s, p, contentEnd);
            }
            p = para.end;
        }
        flushLists(blocks, pendingBullets, pendingOrdered, pendingQuote);
        if (p < s.length()) {
            addPlainGap(s, p, s.length(), blocks);
        }
        return new DocModel.Doc(blocks);
    }

    private static void flushLists(List<DocModel.Block> blocks,
                                   List<List<DocModel.Inline>> bullets,
                                   List<List<DocModel.Inline>> ordered,
                                   List<DocModel.Inline> quote) {
        if (!bullets.isEmpty()) {
            blocks.add(new DocModel.BulletListBlock(new ArrayList<>(bullets)));
            bullets.clear();
        }
        if (!ordered.isEmpty()) {
            blocks.add(new DocModel.OrderedListBlock(new ArrayList<>(ordered)));
            ordered.clear();
        }
        if (quote != null) {
            blocks.add(new DocModel.QuoteBlock(quote));
        }
    }

    /** 未覆盖区间：按行重组 — 连续非空行 → 一个段落（保留软换行），空行 → BlankBlock */
    private static void addPlainGap(Spanned s, int start, int end, List<DocModel.Block> blocks) {
        List<DocModel.Block> out = new ArrayList<>();
        int i = start;
        List<int[]> paraLines = new ArrayList<>();
        while (i < end) {
            int lineEnd = i;
            while (lineEnd < end && s.charAt(lineEnd) != '\n') {
                lineEnd++;
            }
            boolean blank = ParagraphHelper.isBlank(s, i, lineEnd);
            if (blank) {
                if (!paraLines.isEmpty()) {
                    out.add(makeParagraph(s, paraLines));
                    paraLines.clear();
                }
                out.add(new DocModel.BlankBlock());
            } else {
                paraLines.add(new int[]{i, lineEnd});
            }
            i = lineEnd + 1;
        }
        if (!paraLines.isEmpty()) {
            out.add(makeParagraph(s, paraLines));
        }
        blocks.addAll(out);
    }

    private static DocModel.TextBlock makeParagraph(Spanned s, List<int[]> lines) {
        int firstStart = lines.get(0)[0];
        int lastEnd = lines.get(lines.size() - 1)[1];
        return new DocModel.TextBlock(extractInlines(s, firstStart, lastEnd));
    }

    // ---- 行内提取 ----

    private static final int K_LINK = 4;
    private static final int K_BOLD = 3;
    private static final int K_ITALIC = 2;
    private static final int K_STRIKE = 1;
    private static final int K_CODE = 0;

    private record SpanRef(int kind, int start, int end, Object span, String url) {
    }

    private static List<DocModel.Inline> extractInlines(Spanned s, int start, int end) {
        List<SpanRef> refs = new ArrayList<>();
        for (StyleSpan sp : s.getSpans(start, end, StyleSpan.class)) {
            int ss = clamp(s.getSpanStart(sp), start, end);
            int se = clamp(s.getSpanEnd(sp), start, end);
            if (ss < se) {
                int kind = sp.getStyle() == Typeface.BOLD ? K_BOLD : K_ITALIC;
                refs.add(new SpanRef(kind, ss, se, sp, null));
            }
        }
        for (StrikethroughSpan sp : s.getSpans(start, end, StrikethroughSpan.class)) {
            int ss = clamp(s.getSpanStart(sp), start, end);
            int se = clamp(s.getSpanEnd(sp), start, end);
            if (ss < se) {
                refs.add(new SpanRef(K_STRIKE, ss, se, sp, null));
            }
        }
        for (NoteInlineCodeSpan sp : s.getSpans(start, end, NoteInlineCodeSpan.class)) {
            int ss = clamp(s.getSpanStart(sp), start, end);
            int se = clamp(s.getSpanEnd(sp), start, end);
            if (ss < se) {
                refs.add(new SpanRef(K_CODE, ss, se, sp, null));
            }
        }
        for (URLSpan sp : s.getSpans(start, end, URLSpan.class)) {
            int ss = clamp(s.getSpanStart(sp), start, end);
            int se = clamp(s.getSpanEnd(sp), start, end);
            if (ss < se) {
                refs.add(new SpanRef(K_LINK, ss, se, sp, sp.getURL()));
            }
        }
        for (NoteImageSpan sp : s.getSpans(start, end, NoteImageSpan.class)) {
            int ss = clamp(s.getSpanStart(sp), start, end);
            int se = clamp(s.getSpanEnd(sp), start, end);
            if (ss < se) {
                refs.add(new SpanRef(K_LINK, ss, se, sp, sp.getMediaUrl()));
            }
        }
        for (NoteAudioSpan sp : s.getSpans(start, end, NoteAudioSpan.class)) {
            int ss = clamp(s.getSpanStart(sp), start, end);
            int se = clamp(s.getSpanEnd(sp), start, end);
            if (ss < se) {
                refs.add(new SpanRef(K_LINK, ss, se, sp, sp.getMediaUrl()));
            }
        }

        TreeSet<Integer> points = new TreeSet<>();
        points.add(start);
        points.add(end);
        for (SpanRef r : refs) {
            points.add(r.start());
            points.add(r.end());
        }

        List<DocModel.Inline> out = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Integer[] pts = points.toArray(new Integer[0]);
        for (int i = 0; i < pts.length - 1; i++) {
            int segStart = pts[i];
            int segEnd = pts[i + 1];
            if (segStart >= segEnd) {
                continue;
            }
            String segText = s.subSequence(segStart, segEnd).toString();
            List<SpanRef> active = new ArrayList<>();
            for (SpanRef r : refs) {
                if (r.start() <= segStart && r.end() >= segEnd) {
                    active.add(r);
                }
            }

            if (active.isEmpty()) {
                text.append(segText);
                continue;
            }

            // 媒体 span：整段即占位符，直接产出 Image/Audio
            DocModel.Inline mediaNode = mediaNode(active);
            if (mediaNode != null) {
                flush(text, out);
                out.add(mediaNode);
                continue;
            }

            // 链式包裹：由内到外 CODE < STRIKE < ITALIC < BOLD < LINK
            flush(text, out);
            active.sort(Comparator.comparingInt(SpanRef::kind));
            DocModel.Inline node = new DocModel.TextRun(segText);
            for (SpanRef r : active) {
                node = wrap(r, node);
            }
            out.add(node);
        }
        flush(text, out);
        return mergeTextRuns(out);
    }

    private static DocModel.Inline mediaNode(List<SpanRef> active) {
        for (SpanRef r : active) {
            if (r.span() instanceof NoteImageSpan img) {
                return new DocModel.Image(img.getAlt(), img.getMediaUrl());
            }
            if (r.span() instanceof NoteAudioSpan audio) {
                return new DocModel.Link(audio.getMediaUrl(),
                        List.of(new DocModel.TextRun(audio.getAlt())));
            }
        }
        return null;
    }

    private static DocModel.Inline wrap(SpanRef r, DocModel.Inline inner) {
        switch (r.kind()) {
            case K_LINK:
                if (r.span() instanceof URLSpan) {
                    return new DocModel.Link(r.url(), List.of(inner));
                }
                // 媒体 span 已在 mediaNode 分支处理，这里不会到达
                return inner;
            case K_BOLD:
                return new DocModel.Bold(List.of(inner));
            case K_ITALIC:
                return new DocModel.Italic(List.of(inner));
            case K_STRIKE:
                return new DocModel.Strike(List.of(inner));
            case K_CODE:
                return new DocModel.InlineCode(innerText(inner));
            default:
                return inner;
        }
    }

    private static String innerText(DocModel.Inline in) {
        if (in instanceof DocModel.TextRun t) {
            return t.text();
        }
        StringBuilder sb = new StringBuilder();
        collectText(in, sb);
        return sb.toString();
    }

    private static void collectText(DocModel.Inline in, StringBuilder sb) {
        if (in instanceof DocModel.TextRun t) {
            sb.append(t.text());
        } else if (in instanceof DocModel.Bold b) {
            for (DocModel.Inline c : b.children()) {
                collectText(c, sb);
            }
        } else if (in instanceof DocModel.Italic i) {
            for (DocModel.Inline c : i.children()) {
                collectText(c, sb);
            }
        } else if (in instanceof DocModel.Strike st) {
            for (DocModel.Inline c : st.children()) {
                collectText(c, sb);
            }
        }
    }

    private static List<DocModel.Inline> mergeTextRuns(List<DocModel.Inline> in) {
        List<DocModel.Inline> out = new ArrayList<>(in.size());
        StringBuilder pending = new StringBuilder();
        for (DocModel.Inline node : in) {
            if (node instanceof DocModel.TextRun t) {
                pending.append(t.text());
            } else {
                if (pending.length() > 0) {
                    out.add(new DocModel.TextRun(pending.toString()));
                    pending.setLength(0);
                }
                out.add(node);
            }
        }
        if (pending.length() > 0) {
            out.add(new DocModel.TextRun(pending.toString()));
        }
        return out;
    }

    private static void flush(StringBuilder text, List<DocModel.Inline> out) {
        if (text.length() > 0) {
            out.add(new DocModel.TextRun(text.toString()));
            text.setLength(0);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private record Para(Object span, int start, int end) {
    }
}
