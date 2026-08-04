package com.baiflow.note.dto.request;

import jakarta.validation.constraints.Size;

/** 新建笔记请求 */
public record CreateNoteRequest(
        @Size(max = 200, message = "标题不能超过 200 字") String title,
        String content) {
}
