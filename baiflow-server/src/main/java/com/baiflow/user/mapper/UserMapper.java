package com.baiflow.user.mapper;

import com.baiflow.user.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM `user` WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);

    List<User> selectByRole(@Param("role") String role, @Param("status") String status);

    List<User> selectAllOrdered(@Param("role") String role, @Param("status") String status);
}
