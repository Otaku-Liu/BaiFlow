package com.baiflow.event;

/**
 * SSE 事件类型。
 * <p>
 * 通过 {@code GET /api/events}（text/event-stream）推送，事件名即枚举名。
 * 当前仅 {@link #NOTE_UPDATED}（笔记跨端同步刷新）。曾规划的传输/下载/通知事件从未接入，
 * 且 aria2 下载模块已移除，故不再声明。
 */
public enum SseEventType {

    /** 笔记被创建/编辑/删除 */
    NOTE_UPDATED
}
