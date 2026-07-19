package com.baiflow.storage.mapper;

import com.baiflow.storage.entity.StorageRoot;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StorageRootMapper extends BaseMapper<StorageRoot> {
    List<StorageRoot> selectAllOrdered(@Param("status") String status);
    List<StorageRoot> selectByType(@Param("type") String type);
}
