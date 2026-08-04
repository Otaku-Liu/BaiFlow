package com.baiflow.note.dto.response;

import com.baiflow.note.entity.Note;
import com.baiflow.note.enums.NoteStatus;

import java.time.LocalDateTime;

/** 笔记列表项 — 不含正文（正文在详情接口返回），避免列表传输大文本 */
public record NoteSummary(String id, String title, NoteStatus status,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static NoteSummary from(Note n) {
        return new NoteSummary(n.getId(), n.getTitle(), n.getStatus(),
                n.getCreatedAt(), n.getUpdatedAt());
    }
}
