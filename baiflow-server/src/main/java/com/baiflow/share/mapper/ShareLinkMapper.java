package com.baiflow.share.mapper;

import com.baiflow.share.entity.ShareLink;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ShareLinkMapper extends BaseMapper<ShareLink> {
    List<ShareLink> selectByCreator(@Param("createdBy") String createdBy,
                                     @Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);
    int countByCreator(@Param("createdBy") String createdBy, @Param("status") String status);
    /** 管理员查询全部 */
    List<ShareLink> selectAll(@Param("status") String status,
                               @Param("offset") int offset,
                               @Param("limit") int limit);
    int countAll(@Param("status") String status);
}
