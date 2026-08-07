package com.baiflow.note.service.impl;

import com.baiflow.note.entity.NoteProgress;
import com.baiflow.note.mapper.NoteProgressMapper;
import com.baiflow.note.service.NoteProgressService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 笔记进度实体服务实现。
 */
@Service
public class NoteProgressServiceImpl extends ServiceImpl<NoteProgressMapper, NoteProgress> implements NoteProgressService {
}
