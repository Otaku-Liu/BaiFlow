package com.baiflow.note.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 更新笔记请求。
 *
 * @param baseUpdatedAt 本次编辑基于的笔记 updatedAt（乐观并发：若早于服务端当前值则返回 NOTE_CONFLICT）
 */
public record UpdateNoteRequest(
        @Size(max = 200, message = "标题不能超过 200 字") String title,
        String content,
        String baseUpdatedAt) {
}
