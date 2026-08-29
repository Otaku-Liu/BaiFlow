package com.baiflow.note.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量读取笔记媒体请求。
 *
 * @param ids 媒体 ID 列表（≤10；不存在/越权/大文件项会被服务端跳过，客户端回退单个下载）
 */
public record BatchMediaRequest(
        @NotEmpty(message = "媒体 ID 不能为空")
        @Size(max = 10, message = "单次批量最多 10 个媒体")
        List<String> ids) {
}
