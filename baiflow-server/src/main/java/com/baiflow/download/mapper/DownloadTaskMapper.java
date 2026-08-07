package com.baiflow.download.mapper;

import com.baiflow.download.entity.DownloadTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 下载任务 Mapper — 管理 download_task 表的持久化操作。
 */
@Mapper
public interface DownloadTaskMapper extends BaseMapper<DownloadTask> {
}
