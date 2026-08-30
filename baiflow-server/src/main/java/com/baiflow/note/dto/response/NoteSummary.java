package com.baiflow.note.dto.response;

import com.baiflow.note.entity.BfNote;
import com.baiflow.note.enums.NoteStatus;

import java.time.LocalDateTime;

/**
 * 笔记列表项。
 * <p>
 * 普通列表不含正文（正文在详情接口返回，避免列表传输大文本）；增量同步模式
 * （updatedAfter）携带正文（{@link #fromWithContent}），供离线客户端直接合并，
 * 省去逐条 GET 详情（避免 N+1）。
 */
public record NoteSummary(String id, String title, NoteStatus status,
                          LocalDateTime createdAt, LocalDateTime updatedAt, String content) {

    public static NoteSummary from(BfNote n) {
        return new NoteSummary(n.getId(), n.getTitle(), n.getStatus(),
                n.getCreatedAt(), n.getUpdatedAt(), null);
    }

    /** 增量同步模式：携带正文，供离线客户端直接合并 */
    public static NoteSummary fromWithContent(BfNote n) {
        return new NoteSummary(n.getId(), n.getTitle(), n.getStatus(),
                n.getCreatedAt(), n.getUpdatedAt(), n.getContent());
    }
}
