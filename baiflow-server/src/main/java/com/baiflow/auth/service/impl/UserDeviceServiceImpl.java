package com.baiflow.auth.service.impl;

import com.baiflow.auth.entity.UserDevice;
import com.baiflow.auth.mapper.UserDeviceMapper;
import com.baiflow.auth.service.UserDeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户登录设备服务实现。
 */
@Service
public class UserDeviceServiceImpl extends ServiceImpl<UserDeviceMapper, UserDevice>
        implements UserDeviceService {

    @Override
    public void recordLogin(String userId, String deviceName, String deviceType) {
        UserDevice dev = getOne(new LambdaQueryWrapper<UserDevice>()
                .eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceName, deviceName));
        LocalDateTime now = LocalDateTime.now();
        if (dev == null) {
            UserDevice d = new UserDevice();
            d.setUserId(userId);
            d.setDeviceName(deviceName);
            d.setDeviceType(deviceType);
            d.setFirstLoginAt(now);
            d.setLastLoginAt(now);
            save(d);
        } else {
            dev.setLastLoginAt(now);
            dev.setDeviceType(deviceType);
            updateById(dev);
        }
    }
}
