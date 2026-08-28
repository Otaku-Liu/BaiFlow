package com.baiflow.android.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 本地笔记（Room 单表，离线模式三态共用）。
 * <p>
 * - {@link #serverUrl} 为缓存分区键：本地模式 = {@link SessionManager#PARTITION_LOCAL}，否则 = 服务器地址；
 * - {@link #source} 区分 LOCAL_ONLY（从未上传）/ SYNCED（服务端镜像）/ TOMBSTONE（离线删除待同步）；
 * - {@link #dirty} 为 outbox 标记；{@link #conflict} 为同步冲突标记（打开时弹「覆盖/重载」）。
 * 见 docs/05-android.md「离线三态」。
 */
@Entity(tableName = "bf_local_note")
public class LocalNote {
    @PrimaryKey(autoGenerate = true)
    public long id;
    /** 服务端笔记 ID（null = 尚未上传） */
    public String serverId;
    /** 缓存分区键（服务器地址 或 "LOCAL"） */
    public String serverUrl;
    public String title;
    public String content;
    /** 最近一次同步到的服务端 updatedAt（乐观并发基准） */
    public String baseUpdatedAt;
    /** 待同步（outbox） */
    public boolean dirty;
    /** 同步冲突标记（服务端被他人改过） */
    public boolean conflict;
    /** LOCAL_ONLY / SYNCED / TOMBSTONE */
    public String source;
    /** 本地创建时间（epoch millis） */
    public long createdAt;
    /** 本地修改时间（epoch millis） */
    public long updatedAt;
}
