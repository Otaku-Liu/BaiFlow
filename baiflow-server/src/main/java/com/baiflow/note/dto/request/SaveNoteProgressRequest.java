package com.baiflow.note.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;

/** 保存笔记阅读进度请求（滚动百分比 0~1） */
public record SaveNoteProgressRequest(
        @DecimalMin(value = "0.0", message = "进度不能小于 0")
        @DecimalMax(value = "1.0", message = "进度不能大于 1")
        Double positionValue) {
}
