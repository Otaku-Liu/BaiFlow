package com.baiflow.transfer.mapper;

import com.baiflow.transfer.entity.TransferTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 传输任务 Mapper。
 */
@Mapper
public interface TransferTaskMapper extends BaseMapper<TransferTask> {

    /**
     * 查询指定用户的传输任务，支持按任务类型和状态筛选。
     */
    List<TransferTask> selectByUser(@Param("createdBy") String createdBy,
                                    @Param("taskType") String taskType,
                                    @Param("status") String status);
}
