package com.baiflow.file.service.impl;

import com.baiflow.file.entity.PrivateFolderAccess;
import com.baiflow.file.mapper.PrivateFolderAccessMapper;
import com.baiflow.file.service.PrivateFolderAccessService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 隐私文件夹访问会话实体服务实现。
 */
@Service
public class PrivateFolderAccessServiceImpl extends ServiceImpl<PrivateFolderAccessMapper, PrivateFolderAccess> implements PrivateFolderAccessService {
}
