package com.baiflow.note.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.note.dto.request.BatchMediaRequest;
import com.baiflow.note.dto.response.NoteMediaInfo;
import com.baiflow.note.service.BfNoteMediaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 笔记媒体接口控制器 — 上传 / 读取。
 * <p>
 * 媒体独立于文件中心：上传后返回访问 URL（相对路径），写进笔记正文 Markdown 引用；
 * 读取时鉴权（Bearer 或 {@code ?token=} 兜底，供 Web {@code <img>/<audio>} 标签渲染），
 * 非管理员仅能访问自己的媒体，管理员可访问任意媒体。
 */
@RestController
@RequestMapping("/api/notes/media")
public class BfNoteMediaController {

    @Autowired
    private BfNoteMediaService noteMediaService;

    /** 上传笔记媒体（multipart），返回含访问 URL 的信息 */
    @PostMapping
    public ApiResponse<NoteMediaInfo> upload(@RequestParam("file") MultipartFile file,
                                             @RequestParam(required = false) String mediaType,
                                             Authentication auth) {
        return ApiResponse.success(
                noteMediaService.upload(auth.getPrincipal().toString(), file, mediaType));
    }

    /** 读取笔记媒体内容（inline），支持 Bearer 头或 ?token= 查询参数鉴权 */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> serve(@PathVariable String id, Authentication auth) {
        BfNoteMediaService.MediaResource res =
                noteMediaService.load(id, auth.getPrincipal().toString(), isAdmin(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(res.media().getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + sanitizeFilename(res.media().getFileName()) + "\"")
                .body(new FileSystemResource(res.file()));
    }

    /** 批量读取媒体（base64，≤10 个；供 Android 离线缓存，减少 N 次单个下载） */
    @PostMapping("/batch")
    public ApiResponse<Map<String, String>> batch(@Valid @RequestBody BatchMediaRequest req,
                                                  Authentication auth) {
        return ApiResponse.success(
                noteMediaService.batchBase64(auth.getPrincipal().toString(), isAdmin(auth), req.ids()));
    }

    /** 文件名写入 Content-Disposition 前剔除引号/控制字符，避免破坏 quoted-string */
    private static String sanitizeFilename(String name) {
        return name == null ? "media" : name.replaceAll("[\\\"\\r\\n]", "_");
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(ga.getAuthority())) return true;
        }
        return false;
    }
}
