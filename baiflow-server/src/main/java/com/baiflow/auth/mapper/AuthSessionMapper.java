package com.baiflow.auth.mapper;

import com.baiflow.auth.entity.AuthSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {

    /** 按会话 token 的 SHA-256 哈希精确查找会话 */
    @Select("SELECT * FROM bf_auth_session WHERE token_hash = #{tokenHash}")
    AuthSession selectByTokenHash(String tokenHash);
}
