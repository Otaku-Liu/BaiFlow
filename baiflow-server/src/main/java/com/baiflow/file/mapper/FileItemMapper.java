package com.baiflow.file.mapper;

import com.baiflow.file.entity.FileItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FileItemMapper extends BaseMapper<FileItem> {

    /**
     * 递归汇总文件夹子树内所有活跃文件的字节数（MySQL 8 递归 CTE，按 parent_id 树，深度不限）。
     * 特殊 SQL 按项目规范落 XML Mapper（见 mapper/xml/FileItemMapper.xml）。
     */
    Long sumFolderSize(@Param("folderId") String folderId);

    /**
     * 批量统计各父目录的直接活跃子项数（文件 + 子文件夹）。返回行含 parent_id 与 cnt 两列。
     * 特殊 SQL 按项目规范落 XML Mapper（见 mapper/xml/FileItemMapper.xml）。
     */
    List<Map<String, Object>> countChildrenByParents(@Param("parentIds") List<String> parentIds);
}
