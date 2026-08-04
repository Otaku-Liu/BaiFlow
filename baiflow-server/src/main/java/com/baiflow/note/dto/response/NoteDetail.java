package com.baiflow.note.dto.response;

import com.baiflow.note.entity.Note;
import com.baiflow.note.enums.NoteStatus;

import java.time.LocalDateTime;

/** 笔记详情 — 含 Markdown 正文 */
public record NoteDetail(String id, String userId, String title, String content, NoteStatus status,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static NoteDetail from(Note n) {
        return new NoteDetail(n.getId(), n.getUserId(), n.getTitle(), n.getContent(), n.getStatus(),
                n.getCreatedAt(), n.getUpdatedAt());
    }
}
