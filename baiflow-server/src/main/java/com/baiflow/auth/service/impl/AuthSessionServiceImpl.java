package com.baiflow.auth.service.impl;

import com.baiflow.auth.entity.AuthSession;
import com.baiflow.auth.mapper.AuthSessionMapper;
import com.baiflow.auth.service.AuthSessionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 登录会话实体服务实现。
 */
@Service
public class AuthSessionServiceImpl extends ServiceImpl<AuthSessionMapper, AuthSession> implements AuthSessionService {
}
