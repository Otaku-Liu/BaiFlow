package com.baiflow.file.mapper;

import com.baiflow.file.entity.PrivateFolderAccess;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 隐私文件夹访问会话 Mapper — 管理短期访问会话的持久化。
 */
@Mapper
public interface PrivateFolderAccessMapper extends BaseMapper<PrivateFolderAccess> {

    /**
     * 查询指定用户对指定隐私文件夹的有效访问会话（未过期）。
     *
     * @param userId     用户 ID
     * @param fileItemId 隐私文件夹 ID
     * @return 有效会话列表（通常最多一条）
     */
    List<PrivateFolderAccess> selectValidByUserAndFolder(@Param("userId") String userId,
                                                          @Param("fileItemId") String fileItemId);

    /**
     * 删除指定隐私文件夹的所有访问会话（用于密码更新后使旧会话失效）。
     *
     * @param fileItemId 隐私文件夹 ID
     * @return 删除的记录数
     */
    int deleteByFileItemId(@Param("fileItemId") String fileItemId);

    /**
     * 删除过期的访问会话记录（用于定时清理任务）。
     *
     * @return 删除的记录数
     */
    int deleteExpired();
}
