package com.baiflow.file.mapper;

import com.baiflow.file.entity.FileItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileItemMapper extends BaseMapper<FileItem> {
    List<FileItem> selectChildren(@Param("storageRootId") String storageRootId,
                                   @Param("parentId") String parentId,
                                   @Param("status") String status);

    /** 列出指定用户拥有的子文件/目录 */
    List<FileItem> selectChildrenByOwner(@Param("storageRootId") String storageRootId,
                                          @Param("parentId") String parentId,
                                          @Param("ownerUserId") String ownerUserId,
                                          @Param("status") String status);

    FileItem selectByPath(@Param("storageRootId") String storageRootId,
                          @Param("relativePath") String relativePath);
}
