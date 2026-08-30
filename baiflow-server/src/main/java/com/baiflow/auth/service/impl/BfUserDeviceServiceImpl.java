package com.baiflow.auth.service.impl;

import com.baiflow.auth.entity.BfUserDevice;
import com.baiflow.auth.mapper.BfUserDeviceMapper;
import com.baiflow.auth.service.BfUserDeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户登录设备服务实现。
 */
@Service
public class BfUserDeviceServiceImpl extends ServiceImpl<BfUserDeviceMapper, BfUserDevice>
        implements BfUserDeviceService {

    @Override
    public void recordLogin(String userId, String deviceName, String deviceType) {
        BfUserDevice dev = getOne(new LambdaQueryWrapper<BfUserDevice>()
                .eq(BfUserDevice::getUserId, userId)
                .eq(BfUserDevice::getDeviceName, deviceName));
        LocalDateTime now = LocalDateTime.now();
        if (dev == null) {
            BfUserDevice d = new BfUserDevice();
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
