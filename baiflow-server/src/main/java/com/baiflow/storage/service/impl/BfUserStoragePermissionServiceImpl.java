package com.baiflow.storage.service.impl;

import com.baiflow.storage.entity.BfUserStoragePermission;
import com.baiflow.storage.mapper.BfUserStoragePermissionMapper;
import com.baiflow.storage.service.BfUserStoragePermissionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 用户存储权限实体服务实现。
 */
@Service
public class BfUserStoragePermissionServiceImpl extends ServiceImpl<BfUserStoragePermissionMapper, BfUserStoragePermission> implements BfUserStoragePermissionService {
}
