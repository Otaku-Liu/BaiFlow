package com.baiflow.android.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/** 本地笔记 DAO — 分区读写、outbox（dirty）、tombstone、本地模式查询。 */
@Dao
public interface LocalNoteDao {

    /** 某分区的可见笔记列表（不含 TOMBSTONE），按修改时间倒序 */
    @Query("SELECT * FROM bf_local_note WHERE serverUrl = :serverUrl AND source != 'TOMBSTONE' ORDER BY createdAt DESC")
    List<LocalNote> listByServer(String serverUrl);

    /** 某分区关键字搜索（标题/正文模糊） */
    @Query("SELECT * FROM bf_local_note WHERE serverUrl = :serverUrl AND source != 'TOMBSTONE' "
            + "AND (title LIKE :kw OR content LIKE :kw) ORDER BY createdAt DESC")
    List<LocalNote> search(String serverUrl, String kw);

    @Query("SELECT * FROM bf_local_note WHERE id = :id")
    LocalNote getById(long id);

    @Query("SELECT * FROM bf_local_note WHERE serverId = :serverId AND serverUrl = :serverUrl")
    LocalNote getByServerId(String serverId, String serverUrl);

    /** outbox：某分区待同步的笔记（dirty） */
    @Query("SELECT * FROM bf_local_note WHERE serverUrl = :serverUrl AND dirty = 1")
    List<LocalNote> listDirty(String serverUrl);

    /** outbox：某分区的 tombstone（离线删除待同步） */
    @Query("SELECT * FROM bf_local_note WHERE serverUrl = :serverUrl AND source = 'TOMBSTONE'")
    List<LocalNote> listTombstones(String serverUrl);

    /** 本地模式（未配服务器）创建的本地笔记数量 */
    @Query("SELECT COUNT(*) FROM bf_local_note WHERE serverUrl = 'LOCAL' AND source = 'LOCAL_ONLY'")
    int countLocalOnly();

    /** 某分区已同步的服务端镜像笔记数（镜像为空时增量游标无意义，需全量重拉） */
    @Query("SELECT COUNT(*) FROM bf_local_note WHERE serverUrl = :serverUrl AND source = 'SYNCED'")
    int countSynced(String serverUrl);

    /** 本地模式笔记（首次配服务器登录时「上传前询问」用） */
    @Query("SELECT * FROM bf_local_note WHERE serverUrl = 'LOCAL' AND source = 'LOCAL_ONLY'")
    List<LocalNote> listLocalOnly();

    /** 清空某分区缓存（切换服务器/登出时调用，防串号） */
    @Query("DELETE FROM bf_local_note WHERE serverUrl = :serverUrl")
    void clearByServer(String serverUrl);

    @Insert
    long insert(LocalNote note);

    @Update
    void update(LocalNote note);

    @Delete
    void delete(LocalNote note);
}
