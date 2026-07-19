package com.baiflow.transfer.enums;

/** 传输任务状态 */
public enum TransferTaskStatus {
    /** 等待中 */
    WAITING,
    /** 运行中 */
    RUNNING,
    /** 已暂停 */
    PAUSED,
    /** 失败 */
    FAILED,
    /** 已完成 */
    COMPLETED;
}
