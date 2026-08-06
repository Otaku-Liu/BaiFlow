package com.baiflow.note.service;

import com.baiflow.note.dto.response.NoteMediaInfo;
import com.baiflow.note.entity.NoteMedia;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * 笔记媒体服务 — 上传（存专用目录 + 元数据入库）与按 ID 读取（校验所有者/管理员）。
 * <p>
 * 媒体独立于文件中心，不参与 /api/files 列表；笔记正文通过 Markdown 引用媒体。
 */
public interface NoteMediaService {

    /**
     * 上传笔记媒体。
     *
     * @param userId    当前用户 ID（媒体所有者）
     * @param file      上传文件（≤20MB，MIME 在图片/音频白名单内）
     * @param mediaType 客户端声明的媒体类型（IMAGE/AUDIO/DRAWING，可空则按 MIME 推断）
     * @return 媒体信息（含访问 URL {@code /api/notes/media/{id}}）
     */
    NoteMediaInfo upload(String userId, MultipartFile file, String mediaType);

    /**
     * 读取媒体元数据与磁盘文件。
     *
     * @param mediaId 媒体 ID
     * @param userId  当前用户 ID
     * @param isAdmin 是否管理员（非管理员仅能读自己上传的媒体）
     * @return 媒体元数据 + 磁盘文件
     */
    MediaResource load(String mediaId, String userId, boolean isAdmin);

    /** 媒体文件读取结果：元数据 + 磁盘文件 */
    record MediaResource(NoteMedia media, File file) {
    }
}
