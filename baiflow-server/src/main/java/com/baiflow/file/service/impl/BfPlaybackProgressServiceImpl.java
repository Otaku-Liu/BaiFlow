package com.baiflow.file.service.impl;

import com.baiflow.file.entity.BfPlaybackProgress;
import com.baiflow.file.mapper.BfPlaybackProgressMapper;
import com.baiflow.file.service.BfPlaybackProgressService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 播放进度实体服务实现。
 */
@Service
public class BfPlaybackProgressServiceImpl extends ServiceImpl<BfPlaybackProgressMapper, BfPlaybackProgress> implements BfPlaybackProgressService {
}
