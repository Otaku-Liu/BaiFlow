package com.baiflow.auth.service.impl;

import com.baiflow.auth.entity.BfAuthSession;
import com.baiflow.auth.mapper.BfAuthSessionMapper;
import com.baiflow.auth.service.BfAuthSessionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 登录会话实体服务实现。
 */
@Service
public class BfAuthSessionServiceImpl extends ServiceImpl<BfAuthSessionMapper, BfAuthSession> implements BfAuthSessionService {
}
