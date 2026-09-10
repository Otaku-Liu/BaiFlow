package com.baiflow.setup.service.impl;

import com.baiflow.setup.entity.BfSystemSetting;
import com.baiflow.setup.mapper.BfSystemSettingMapper;
import com.baiflow.setup.service.BfSystemSettingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 系统设置实体服务实现。
 */
@Service
public class BfSystemSettingServiceImpl extends ServiceImpl<BfSystemSettingMapper, BfSystemSetting>
        implements BfSystemSettingService {
}
