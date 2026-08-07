package com.baiflow.note.service.impl;

import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.event.SseEventType;
import com.baiflow.event.SseService;
import com.baiflow.note.dto.response.NoteDetail;
import com.baiflow.note.dto.response.NoteSummary;
import com.baiflow.note.entity.Note;
import com.baiflow.note.entity.NoteProgress;
import com.baiflow.note.enums.NoteStatus;
import com.baiflow.note.mapper.NoteMapper;
import com.baiflow.note.mapper.NoteProgressMapper;
import com.baiflow.note.service.NoteService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 随手记服务实现 — 非管理员仅能操作自己的笔记；管理员可全量访问（viewUserId 用于列表切换）。
 * 每次写操作后向笔记所有者推送 {@link SseEventType#NOTE_UPDATED} 事件。
 */
@Slf4j
@Service
public class NoteServiceImpl implements NoteService {

    @Autowired
    private NoteMapper noteMapper;
    @Autowired
    private NoteProgressMapper progressMapper;
    @Autowired
    private SseService sseService;

    @Override
    public IPage<NoteSummary> listNotes(String userId, boolean isAdmin, String viewUserId,
                                        String keyword, int page, int size, String updatedAfter) {
        QueryWrapper<Note> wrapper = new QueryWrapper<>();
        // 非管理员始终限定本人；管理员不传 viewUserId 则看全部
        if (!isAdmin || viewUserId != null) {
            wrapper.eq("user_id", isAdmin ? viewUserId : userId);
        }
        // 增量同步模式：只取该时间之后的更新，并包含软删除（客户端据此同步删除）
        boolean incremental = updatedAfter != null && !updatedAfter.isBlank();
        if (incremental) {
            LocalDateTime ts = parseBaseTimestamp(updatedAfter);
            if (ts != null) {
                wrapper.gt("updated_at", ts);
            }
        } else {
            wrapper.eq("status", NoteStatus.ACTIVE.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("title", keyword).or().like("content", keyword));
        }
        wrapper.orderByDesc("updated_at");

        IPage<Note> pageResult = noteMapper.selectPage(new Page<>(page, size), wrapper);
        return pageResult.convert(NoteSummary::from);
    }

    @Override
    @Transactional
    public NoteDetail createNote(String userId, String title, String content) {
        Note note = new Note();
        note.setUserId(userId);
        note.setTitle(title != null ? title : "");
        note.setContent(content != null ? content : "");
        note.setStatus(NoteStatus.ACTIVE);
        noteMapper.insert(note);
        // 重新查询以回填数据库生成的时间戳（created_at/updated_at）
        Note saved = noteMapper.selectById(note.getId());
        publishUpdated(userId, saved);
        return NoteDetail.from(saved);
    }

    @Override
    public NoteDetail getNote(String id, String userId, boolean isAdmin) {
        Note note = requireNote(id);
        checkAccess(note, userId, isAdmin);
        return NoteDetail.from(note);
    }

    @Override
    @Transactional
    public NoteDetail updateNote(String id, String userId, boolean isAdmin,
                                 String title, String content, String baseUpdatedAt) {
        Note note = requireNote(id);
        checkAccess(note, userId, isAdmin);
        // 乐观并发：客户端基于的 updatedAt 早于服务端当前值 → 说明被其他设备改过，返回冲突
        if (baseUpdatedAt != null && !baseUpdatedAt.isBlank()) {
            LocalDateTime base = parseBaseTimestamp(baseUpdatedAt);
            if (base != null && note.getUpdatedAt() != null && note.getUpdatedAt().isAfter(base)) {
                throw new BusinessException(ErrorCode.NOTE_CONFLICT, "笔记已在其他设备被修改");
            }
        }
        if (title != null) note.setTitle(title);
        if (content != null) note.setContent(content);
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.updateById(note);
        publishUpdated(note.getUserId(), note);
        return NoteDetail.from(note);
    }

    /** 解析客户端回传的 ISO 时间戳；无法解析返回 null（跳过并发校验，不阻塞保存） */
    private LocalDateTime parseBaseTimestamp(String s) {
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional
    public void deleteNote(String id, String userId, boolean isAdmin) {
        Note note = requireNote(id);
        checkAccess(note, userId, isAdmin);
        note.setStatus(NoteStatus.DELETED);
        note.setDeletedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.updateById(note);
        publishUpdated(note.getUserId(), note);
    }

    @Override
    public Map<String, Object> getNoteProgress(String noteId, String userId) {
        NoteProgress p = progressMapper.selectByUserAndNote(userId, noteId);
        if (p == null) return null;
        return Map.of(
                "noteId", p.getNoteId(),
                "positionType", p.getPositionType(),
                "positionValue", p.getPositionValue(),
                "updatedAt", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : "");
    }

    @Override
    public void saveNoteProgress(String noteId, String userId, Double positionValue) {
        // 校验笔记存在且未删除，避免为不存在的笔记写入孤儿进度行
        requireNote(noteId);
        String id = UUID.randomUUID().toString().replace("-", "");
        progressMapper.upsert(id, userId, noteId, "SCROLL_PERCENT",
                positionValue != null ? positionValue : 0);
    }

    // ---- 私有辅助 ----

    /** 加载笔记，不存在或已删除则抛 NOT_FOUND */
    private Note requireNote(String id) {
        Note note = noteMapper.selectById(id);
        if (note == null || note.getStatus() != NoteStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "笔记不存在");
        }
        return note;
    }

    /** 非管理员只能操作自己的笔记 */
    private void checkAccess(Note note, String userId, boolean isAdmin) {
        if (!isAdmin && !userId.equals(note.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此笔记");
        }
    }

    /** 向笔记所有者推送 NOTE_UPDATED 事件（事件数据为 noteId + updatedAt） */
    private void publishUpdated(String ownerUserId, Note note) {
        sseService.publish(ownerUserId, SseEventType.NOTE_UPDATED,
                Map.of("noteId", note.getId(),
                        "updatedAt", note.getUpdatedAt() != null ? note.getUpdatedAt().toString() : ""));
    }
}
