package com.baiflow.file.mapper;

import com.baiflow.file.entity.PlaybackProgress;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlaybackProgressMapper extends BaseMapper<PlaybackProgress> {

    /** 按用户和文件查找进度记录 */
    PlaybackProgress selectByUserAndFile(@Param("userId") String userId,
                                         @Param("fileItemId") String fileItemId);
}
