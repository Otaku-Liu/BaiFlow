package com.baiflow.transfer.mapper;

import com.baiflow.transfer.entity.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知 Mapper。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 查询指定用户的未读通知数量。
     */
    long countUnread(@Param("userId") String userId);

    /**
     * 查询指定用户的通知，支持按阅读状态筛选。
     */
    List<Notification> selectByUser(@Param("userId") String userId,
                                     @Param("readStatus") String readStatus);
}
