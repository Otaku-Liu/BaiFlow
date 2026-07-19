package com.baiflow.download.enums;

/** 下载任务状态 */
public enum DownloadTaskStatus {
    /** 等待中（已提交给 aria2，等待开始） */
    WAITING,
    /** 下载中 */
    RUNNING,
    /** 已暂停 */
    PAUSED,
    /** 下载失败 */
    FAILED,
    /** 下载完成 */
    COMPLETED,
    /** 已删除（逻辑删除） */
    DELETED
}
