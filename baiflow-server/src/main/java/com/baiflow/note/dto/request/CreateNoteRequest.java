package com.baiflow.note.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建笔记请求。
 *
 * <p>{@code id} 可选，由**客户端**生成（32 位十六进制，与 MP 的 ASSIGN_UUID 同形）并**在重试之间保持不变**：
 * 服务端按它插入，撞主键说明是同一次创建的重发 —— 直接返回已存在的那条（见 {@code BfNoteService#createNote}）。
 * 不带 id 时行为不变（服务端生成）。
 */
public record CreateNoteRequest(
        @Pattern(regexp = "^[0-9a-fA-F]{32}$", message = "笔记 ID 格式不正确")
        String id,
        @Size(max = 200, message = "标题不能超过 200 字") String title,
        String content) {
}
