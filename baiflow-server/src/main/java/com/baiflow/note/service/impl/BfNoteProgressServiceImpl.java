package com.baiflow.note.service.impl;

import com.baiflow.note.entity.BfNoteProgress;
import com.baiflow.note.mapper.BfNoteProgressMapper;
import com.baiflow.note.service.BfNoteProgressService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 笔记进度实体服务实现。
 */
@Service
public class BfNoteProgressServiceImpl extends ServiceImpl<BfNoteProgressMapper, BfNoteProgress> implements BfNoteProgressService {
}
