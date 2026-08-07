package com.baiflow.file.mapper;

import com.baiflow.file.entity.PrivateFolderAccess;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 隐私文件夹访问会话 Mapper — 管理短期访问会话的持久化。
 */
@Mapper
public interface PrivateFolderAccessMapper extends BaseMapper<PrivateFolderAccess> {
}
