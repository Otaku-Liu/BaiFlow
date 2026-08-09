package com.baiflow.auth.mapper;

import com.baiflow.auth.entity.UserDevice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 用户登录设备 Mapper */
@Mapper
public interface UserDeviceMapper extends BaseMapper<UserDevice> {
}
