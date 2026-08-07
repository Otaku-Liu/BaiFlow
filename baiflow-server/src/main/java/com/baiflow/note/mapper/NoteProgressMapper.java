package com.baiflow.note.mapper;

import com.baiflow.note.entity.NoteProgress;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NoteProgressMapper extends BaseMapper<NoteProgress> {

    /**
     * 原子 upsert：命中 (user_id, note_id) 唯一键则更新，否则插入。
     * 避免 select-then-insert 并发下撞唯一键导致 duplicate-key 错误。
     * 实现保留在 XML（MySQL 原生 ON DUPLICATE KEY UPDATE）。
     */
    int upsert(@Param("id") String id,
               @Param("userId") String userId,
               @Param("noteId") String noteId,
               @Param("positionType") String positionType,
               @Param("positionValue") Double positionValue);
}
