package com.baiflow.android.util;

import com.baiflow.android.R;

import java.util.Locale;
import java.util.Set;

/**
 * 文件类型图标解析（彩色 PNG；md/json/xml 独立，其它 text 共用文件图标）。
 * 文件中心与传输记录行共用；mime 未知（上传占位行、记录行）传 null，仅按扩展名兜底识别。
 */
public final class FileTypeIcon {

    private FileTypeIcon() {
    }

    /** 文件夹图标 */
    public static int forDirectory() {
        return R.drawable.ic_folder;
    }

    /** 按文件名 + mime 解析文件图标；mime 为 null 时仅按扩展名识别 */
    public static int forName(String name, String mime) {
        // md 优先按扩展名识别：服务端存的 mime 是上传方 Content-Type，.md 可能不是 text/markdown
        if (isMarkdown(name, mime)) return R.drawable.ic_type_md;
        if (mime != null) {
            if (mime.startsWith("image/")) return R.drawable.ic_type_image;
            if (mime.startsWith("video/")) return R.drawable.ic_type_video;
            if (mime.startsWith("audio/")) return R.drawable.ic_type_audio;
            if ("application/pdf".equals(mime)) return R.drawable.ic_type_pdf;
            if (mime.endsWith("json")) return R.drawable.ic_type_json;
            if (mime.endsWith("xml")) return R.drawable.ic_type_xml;
            if (mime.startsWith("text/")) return R.drawable.ic_type_file;
            if (mime.contains("msword") || mime.contains("wordprocessingml")) return R.drawable.ic_type_word;
            if (mime.contains("ms-excel") || mime.contains("spreadsheetml")) return R.drawable.ic_type_excel;
            if (mime.contains("ms-powerpoint") || mime.contains("presentationml")) return R.drawable.ic_type_ppt;
            if (mime.contains("zip") || mime.contains("compressed") || mime.contains("x-tar")
                    || mime.contains("gzip")) return R.drawable.ic_type_archive;
        }
        // 扩展名兜底：服务端 mime 可能被上传方硬编码为 application/octet-stream，按扩展名识别常见类型
        // （与 Web fileIconPath 兜底保持一致，保证同文件两端图标一致）
        String ext = extensionOf(name);
        if (ext != null) {
            if (VIDEO_EXTS.contains(ext)) return R.drawable.ic_type_video;
            if (AUDIO_EXTS.contains(ext)) return R.drawable.ic_type_audio;
            if (IMAGE_EXTS.contains(ext)) return R.drawable.ic_type_image;
            if ("pdf".equals(ext)) return R.drawable.ic_type_pdf;
            if (ARCHIVE_EXTS.contains(ext)) return R.drawable.ic_type_archive;
            if ("doc".equals(ext) || "docx".equals(ext)) return R.drawable.ic_type_word;
            if ("xls".equals(ext) || "xlsx".equals(ext) || "csv".equals(ext)) return R.drawable.ic_type_excel;
            if ("ppt".equals(ext) || "pptx".equals(ext)) return R.drawable.ic_type_ppt;
            if ("json".equals(ext)) return R.drawable.ic_type_json;
            if ("xml".equals(ext)) return R.drawable.ic_type_xml;
        }
        return R.drawable.ic_type_file;
    }

    private static final Set<String> VIDEO_EXTS =
            Set.of("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "mpeg", "mpg");
    private static final Set<String> AUDIO_EXTS =
            Set.of("mp3", "wav", "aac", "flac", "m4a", "ogg", "opus", "wma", "amr");
    private static final Set<String> IMAGE_EXTS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg");
    private static final Set<String> ARCHIVE_EXTS =
            Set.of("zip", "rar", "7z", "gz", "tar", "bz2", "xz");

    /** 取小写扩展名（不含点）；无扩展名返回 null */
    private static String extensionOf(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 是否 Markdown 文件：扩展名 .md/.markdown，或 MIME 含 markdown */
    private static boolean isMarkdown(String name, String mime) {
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".md") || lower.endsWith(".markdown")) return true;
        }
        return mime != null && mime.contains("markdown");
    }
}
