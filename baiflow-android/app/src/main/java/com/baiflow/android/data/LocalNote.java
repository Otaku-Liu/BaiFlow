package com.baiflow.android.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 本地笔记（Room 单表，按服务器地址分区缓存）。
 * <p>
 * - {@link #serverUrl} 为缓存分区键 = 服务器地址；
 * - {@link #source} 区分 LOCAL_ONLY（从未上传）/ SYNCED（服务端镜像）/ TOMBSTONE（离线删除待同步）；
 * - {@link #dirty} 为 outbox 标记；{@link #conflict} 为同步冲突标记（打开时弹「覆盖/重载」）。
 * 见 docs/05-android.md「离线两态」。
 */
@Entity(tableName = "bf_local_note")
public class LocalNote {
    @PrimaryKey(autoGenerate = true)
    public long id;
    /** 服务端笔记 ID（null = 尚未上传） */
    public String serverId;
    /**
     * 客户端生成的笔记 ID（32 位十六进制）：**新建时生成一次**，推送 CREATE 重试之间保持不变 ——
     * 服务端按它插入，重发即幂等（返回第一次创建的那条），不会因响应丢包而产生重复笔记。
     * 服务端已存在、本地镜像的笔记为 null（不会作为 create 推送）。
     */
    public String clientId;
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
