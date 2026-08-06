package com.baiflow.note.dto.response;

import com.baiflow.note.entity.NoteMedia;

/** 笔记媒体上传响应 — 包含访问 URL（相对路径，各端拼接服务器地址后访问） */
public record NoteMediaInfo(String id, String mediaType, String url, String mimeType,
                            long sizeBytes, String createdAt) {
    public static NoteMediaInfo from(NoteMedia m) {
        return new NoteMediaInfo(m.getId(), m.getMediaType().name(),
                "/api/notes/media/" + m.getId(),
                m.getMimeType(),
                m.getSizeBytes() != null ? m.getSizeBytes() : 0L,
                m.getCreatedAt() != null ? m.getCreatedAt().toString() : "");
    }
}
