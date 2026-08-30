package com.baiflow.file.service.impl;

import com.baiflow.file.entity.BfPrivateFolderAccess;
import com.baiflow.file.mapper.BfPrivateFolderAccessMapper;
import com.baiflow.file.service.BfPrivateFolderAccessService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 隐私文件夹访问会话实体服务实现。
 */
@Service
public class BfPrivateFolderAccessServiceImpl extends ServiceImpl<BfPrivateFolderAccessMapper, BfPrivateFolderAccess> implements BfPrivateFolderAccessService {
}
