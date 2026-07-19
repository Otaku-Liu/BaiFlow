package com.baiflow.user.service.impl;

import com.baiflow.common.entity.ApiResponse.Code;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.user.dto.request.CreateUserRequest;
import com.baiflow.user.dto.request.ResetPasswordRequest;
import com.baiflow.user.dto.request.UpdateUserRequest;
import com.baiflow.user.dto.response.UserInfo;
import com.baiflow.user.entity.User;
import com.baiflow.user.enums.UserStatus;
import com.baiflow.user.mapper.UserMapper;
import com.baiflow.user.service.UserService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户管理服务实现。
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserInfo createUser(CreateUserRequest req) {
        // 用户名重复检查
        if (userMapper.selectByUsername(req.username()) != null) {
            throw new BusinessException(Code.USERNAME_EXISTS, "用户名已存在：" + req.username());
        }

        User u = new User();
        u.setUsername(req.username());
        // 密码 BCrypt 哈希后再存储——绝不存明文
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setDisplayName(req.displayName() != null ? req.displayName() : "");
        u.setRole(req.role());
        u.setStatus(UserStatus.ACTIVE);
        userMapper.insert(u);
        return UserInfo.from(u);
    }

    @Override
    public IPage<UserInfo> listUsers(int page, int size, String role, String status) {
        // 从数据库加载全量数据后在内存中进行分页处理（MVP 阶段用户量较小，可以接受）
        List<User> users = (role != null || status != null)
                ? userMapper.selectByRole(role, status)
                : userMapper.selectAllOrdered(null, null);
        int total = users.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<UserInfo> records = (from < total ? users.subList(from, to) : List.<User>of())
                .stream().map(UserInfo::from).toList();
        IPage<UserInfo> r = new Page<>(page, size, total);
        r.setRecords(records);
        return r;
    }

    @Override
    public UserInfo getUser(String id) {
        User u = userMapper.selectById(id);
        if (u == null) { throw new BusinessException(Code.NOT_FOUND, "用户不存在"); }
        return UserInfo.from(u);
    }

    @Override
    public UserInfo updateUser(String id, UpdateUserRequest req) {
        User u = userMapper.selectById(id);
        if (u == null) { throw new BusinessException(Code.NOT_FOUND, "用户不存在"); }
        // 仅更新实际传入的字段
        if (req.displayName() != null) { u.setDisplayName(req.displayName()); }
        if (req.role() != null) { u.setRole(req.role()); }
        if (req.status() != null) { u.setStatus(req.status()); }
        userMapper.updateById(u);
        return UserInfo.from(u);
    }

    @Override
    public void resetPassword(String id, ResetPasswordRequest req) {
        User u = userMapper.selectById(id);
        if (u == null) { throw new BusinessException(Code.NOT_FOUND, "用户不存在"); }
        // 新密码重新 BCrypt 哈希，完全覆盖旧密码
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userMapper.updateById(u);
    }
}
