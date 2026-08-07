package com.baiflow.note.service;

import com.baiflow.note.dto.response.NoteDetail;
import com.baiflow.note.dto.response.NoteSummary;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Map;

/**
 * 随手记服务 — 笔记 CRUD、搜索与阅读进度。
 * <p>
 * 权限：非管理员只能访问自己的笔记（ownerUserId 隔离）；管理员可查看全部，
 * 传 {@code viewUserId} 时限定到指定用户。笔记独立于文件系统，不受存储根目录/隐私约束。
 */
public interface NoteService {

    /**
     * 分页列出笔记。
     * <p>
     * 传 {@code updatedAfter} 时为增量同步模式：返回该时间之后更新的笔记，且**包含软删除**
     * （客户端据此同步删除）；不传时仅返回 ACTIVE 笔记（普通列表）。
     *
     * @param userId        调用者 ID
     * @param isAdmin       是否 ADMIN
     * @param viewUserId    管理员视角切换（可空）
     * @param keyword       标题/正文模糊搜索（可空）
     * @param page          页码（从 1 开始）
     * @param size          每页数量
     * @param updatedAfter  增量基准时间（ISO，可空）
     * @return 笔记列表（不含正文），按更新时间倒序
     */
    IPage<NoteSummary> listNotes(String userId, boolean isAdmin, String viewUserId,
                                 String keyword, int page, int size, String updatedAfter);

    /**
     * 新建笔记。
     *
     * @param userId  创建者（即笔记所有者）
     * @param title   标题
     * @param content Markdown 正文
     */
    NoteDetail createNote(String userId, String title, String content);

    /**
     * 查询笔记详情（含正文）。
     *
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND  笔记不存在
     * @throws com.baiflow.common.exception.BusinessException FORBIDDEN  非所有者访问他人笔记
     */
    NoteDetail getNote(String id, String userId, boolean isAdmin);

    /**
     * 更新笔记标题/正文，刷新 updated_at（后写覆盖同步基准）。
     *
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND  笔记不存在
     * @throws com.baiflow.common.exception.BusinessException FORBIDDEN  非所有者修改他人笔记
     */
    NoteDetail updateNote(String id, String userId, boolean isAdmin, String title, String content,
                          String baseUpdatedAt);

    /**
     * 软删除笔记（status=DELETED），并推送 NOTE_UPDATED。
     *
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND  笔记不存在
     * @throws com.baiflow.common.exception.BusinessException FORBIDDEN  非所有者删除他人笔记
     */
    void deleteNote(String id, String userId, boolean isAdmin);

    /** 查询当前用户对某笔记的阅读进度，无记录返回 null */
    Map<String, Object> getNoteProgress(String noteId, String userId);

    /** 保存或更新阅读进度（upsert，按 user_id + note_id 唯一键） */
    void saveNoteProgress(String noteId, String userId, Double positionValue);
}
