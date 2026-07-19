package com.baiflow.download.mapper;

import com.baiflow.download.entity.DownloadTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 下载任务 Mapper — 管理 download_task 表的持久化操作。
 */
@Mapper
public interface DownloadTaskMapper extends BaseMapper<DownloadTask> {

    /**
     * 按用户 ID 和状态分页查询下载任务列表（按创建时间倒序）。
     */
    List<DownloadTask> selectByUser(@Param("userId") String userId,
                                     @Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 统计指定用户和状态的下载任务数。
     */
    int countByUser(@Param("userId") String userId, @Param("status") String status);

    /**
     * 查询所有非同终态的下载任务（WAITING / RUNNING / PAUSED 状态），用于定时状态同步。
     */
    List<DownloadTask> selectActive();

    /**
     * 按 aria2 GID 查询下载任务。
     */
    DownloadTask selectByAria2Gid(@Param("aria2Gid") String aria2Gid);
}
