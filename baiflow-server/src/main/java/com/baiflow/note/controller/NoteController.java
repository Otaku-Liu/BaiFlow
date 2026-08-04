package com.baiflow.note.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.note.dto.request.CreateNoteRequest;
import com.baiflow.note.dto.request.SaveNoteProgressRequest;
import com.baiflow.note.dto.request.UpdateNoteRequest;
import com.baiflow.note.dto.response.NoteDetail;
import com.baiflow.note.dto.response.NoteSummary;
import com.baiflow.note.service.NoteService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 随手记接口控制器。
 * <p>
 * 非管理员只能访问自己的笔记；管理员可查看全部，并通过 {@code viewUserId} 切换视角。
 * 阅读进度按 (user_id, note_id) 独立存储。
 */
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    @Autowired
    private NoteService noteService;

    /** 分页列出笔记，支持关键字搜索（标题/正文）与管理员视角切换 */
    @GetMapping
    public ApiResponse<IPage<NoteSummary>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String viewUserId,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "50") int size,
                                                 Authentication auth) {
        return ApiResponse.success(
                noteService.listNotes(auth.getPrincipal().toString(), isAdmin(auth), viewUserId,
                        keyword, page, size));
    }

    /** 新建笔记 */
    @PostMapping
    public ApiResponse<NoteDetail> create(@Valid @RequestBody CreateNoteRequest req,
                                          Authentication auth) {
        return ApiResponse.success(
                noteService.createNote(auth.getPrincipal().toString(), req.title(), req.content()));
    }

    /** 查询笔记详情（含 Markdown 正文） */
    @GetMapping("/{id}")
    public ApiResponse<NoteDetail> detail(@PathVariable String id, Authentication auth) {
        return ApiResponse.success(
                noteService.getNote(id, auth.getPrincipal().toString(), isAdmin(auth)));
    }

    /** 更新笔记标题/正文 */
    @PatchMapping("/{id}")
    public ApiResponse<NoteDetail> update(@PathVariable String id,
                                          @Valid @RequestBody UpdateNoteRequest req,
                                          Authentication auth) {
        return ApiResponse.success(
                noteService.updateNote(id, auth.getPrincipal().toString(), isAdmin(auth),
                        req.title(), req.content()));
    }

    /** 软删除笔记 */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id, Authentication auth) {
        noteService.deleteNote(id, auth.getPrincipal().toString(), isAdmin(auth));
        return ApiResponse.success(Map.of("result", "已删除"));
    }

    /** 查询当前用户对某笔记的阅读进度 */
    @GetMapping("/{id}/progress")
    public ApiResponse<Map<String, Object>> getProgress(@PathVariable String id, Authentication auth) {
        return ApiResponse.success(
                noteService.getNoteProgress(id, auth.getPrincipal().toString()));
    }

    /** 保存当前用户对某笔记的阅读进度（滚动百分比 0~1） */
    @PutMapping("/{id}/progress")
    public ApiResponse<Map<String, Object>> saveProgress(@PathVariable String id,
                                                          @Valid @RequestBody SaveNoteProgressRequest req,
                                                          Authentication auth) {
        noteService.saveNoteProgress(id, auth.getPrincipal().toString(), req.positionValue());
        return ApiResponse.success(Map.of("result", "已保存"));
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(ga.getAuthority())) return true;
        }
        return false;
    }
}
