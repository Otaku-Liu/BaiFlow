package com.baiflow.auth.service;

import com.baiflow.auth.entity.BfUserDevice;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户登录设备服务 — 登录时登记/更新设备历史。
 */
public interface BfUserDeviceService extends IService<BfUserDevice> {

    /**
     * 登录成功后登记设备：已存在（user + device_name）则更新最近登录时间，否则新建。
     *
     * @param userId     用户 ID
     * @param deviceName 设备名
     * @param deviceType 设备类型（ANDROID / WEB）
     */
    void recordLogin(String userId, String deviceName, String deviceType);
}
