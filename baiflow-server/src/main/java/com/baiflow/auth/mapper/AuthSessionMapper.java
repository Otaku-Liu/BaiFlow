package com.baiflow.auth.mapper;

import com.baiflow.auth.entity.AuthSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {
}
