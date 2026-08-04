package com.baiflow.event;

/**
 * SSE 事件类型。
 * <p>
 * 通过 {@code GET /api/events}（text/event-stream）推送，事件名即枚举名。
 * 当前已实现 {@link #NOTE_UPDATED}；其余为文档规划的传输/通知事件，待各模块接入。
 */
public enum SseEventType {

    /** 笔记被创建/编辑/删除 */
    NOTE_UPDATED,

    /** 传输任务进度 */
    TRANSFER_PROGRESS,

    /** 下载完成 */
    DOWNLOAD_COMPLETED,

    /** 下载失败 */
    DOWNLOAD_FAILED,

    /** 新通知 */
    NOTIFICATION_CREATED
}
