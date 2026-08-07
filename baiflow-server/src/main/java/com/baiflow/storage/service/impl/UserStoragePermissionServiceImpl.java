package com.baiflow.storage.service.impl;

import com.baiflow.storage.entity.UserStoragePermission;
import com.baiflow.storage.mapper.UserStoragePermissionMapper;
import com.baiflow.storage.service.UserStoragePermissionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 用户存储权限实体服务实现。
 */
@Service
public class UserStoragePermissionServiceImpl extends ServiceImpl<UserStoragePermissionMapper, UserStoragePermission> implements UserStoragePermissionService {
}
