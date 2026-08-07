package com.baiflow.file.service.impl;

import com.baiflow.file.entity.PlaybackProgress;
import com.baiflow.file.mapper.PlaybackProgressMapper;
import com.baiflow.file.service.PlaybackProgressService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 播放进度实体服务实现。
 */
@Service
public class PlaybackProgressServiceImpl extends ServiceImpl<PlaybackProgressMapper, PlaybackProgress> implements PlaybackProgressService {
}
