package com.baiflow.user.mapper;

import com.baiflow.user.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM `user` WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);

    List<User> selectByRole(@Param("role") String role, @Param("status") String status, @Param("displayName") String displayName);

    List<User> selectAllOrdered(@Param("role") String role, @Param("status") String status, @Param("displayName") String displayName);

    /** MyBatis-Plus 分页查询用户列表 */
    Page<User> selectPage(Page<User> page,
                          @Param("role") String role,
                          @Param("status") String status,
                          @Param("displayName") String displayName);
}
