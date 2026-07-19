package com.baiflow.storage.mapper;

import com.baiflow.storage.entity.UserStoragePermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserStoragePermissionMapper extends BaseMapper<UserStoragePermission> {
    List<UserStoragePermission> selectByUser(@Param("userId") String userId);
    UserStoragePermission selectByUserAndRoot(@Param("userId") String userId, @Param("storageRootId") String storageRootId);
}
