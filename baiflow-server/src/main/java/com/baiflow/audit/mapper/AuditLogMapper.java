package com.baiflow.audit.mapper;

import com.baiflow.audit.dto.response.LoginLogVO;
import com.baiflow.audit.entity.AuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 审计日志 Mapper — 包含登录日志查询等管理端功能。 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /** 分页查询登录日志（含用户信息）。参数为可选筛选条件。 */
    Page<LoginLogVO> selectLoginLogs(Page<LoginLogVO> page,
                                     @Param("username") String username,
                                     @Param("status") String status,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate);
}
