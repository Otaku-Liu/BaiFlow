package com.baiflow.android.editor;

import android.graphics.Typeface;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.style.URLSpan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Spannable 行内 → Doc（块所见即所得回写，经 {@link BlockRichText} 使用）。
 * 行内格式用「边界切分 + 固定优先级链式包裹」近似嵌套（重叠 span 摊平为相邻节点，
 * 文本不丢失，属 v1 接受的取舍）。
 */
public final class SpanExtractor {

    private SpanExtractor() {
    }

    private static final int K_LINK = 5;
    private static final int K_UNDERLINE = 4;
    private static final int K_BOLD = 3;
    private static final int K_ITALIC = 2;
    private static final int K_STRIKE = 1;
    private static final int K_CODE = 0;

    private record SpanRef(int kind, int start, int end, Object span, String url) {
    }

    static List<DocModel.Inline> extractInlines(Spanned s, int start, int end) {
        List<SpanRef> refs = new ArrayList<>();
        for (StyleSpan sp : s.getSpans(start, end, StyleSpan.class)) {
            int ss = clamp(s.getSpanStart(sp), start, end);
            int se = clamp(s.getSpanEnd(sp), start, end);
            if (ss < se) {
                int kind = sp.getStyle() == Typeface.BOLD ? K_BOLD : K_ITALIC;
                refs.add(new SpanRef(kind, ss, se, sp, null));
            }
        }
        for (UnderlineSpan sp : s.getSpans(start, end, UnderlineSpan.class)) {
            int ss = clamp(s.getSpanStart(sp), start, end);
            int se = clamp(s.getSpanEnd(sp), start, end);
            if (ss < se) {
                refs.add(new SpanRef(K_UNDERLINE, ss, se, sp, null));
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

            // 链式包裹：由内到外 CODE < STRIKE < ITALIC < BOLD < UNDERLINE < LINK
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
                return new DocModel.Link(r.url(), List.of(inner));
            case K_BOLD:
                return new DocModel.Bold(List.of(inner));
            case K_ITALIC:
                return new DocModel.Italic(List.of(inner));
            case K_UNDERLINE:
                return new DocModel.Underline(List.of(inner));
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
}
