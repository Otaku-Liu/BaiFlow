package com.baiflow.note.service.impl;

import com.baiflow.auth.config.BaiflowProperties;
import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.common.util.I18nUtil;
import com.baiflow.note.dto.response.NoteMediaInfo;
import com.baiflow.note.entity.BfNoteMedia;
import com.baiflow.note.enums.NoteMediaType;
import com.baiflow.note.mapper.BfNoteMediaMapper;
import com.baiflow.note.service.BfNoteMediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 笔记媒体服务实现 — 上传校验（大小 ≤20MB、MIME 白名单、类型与格式匹配），
 * 文件落盘到 {@code baiflow.notes.media-path} 专用目录（文件名 {@code <mediaId>.<ext>}，
 * 由服务端 UUID 生成，无路径穿越风险），元数据入库 {@code bf_note_media}。
 * <p>
 * 读取时校验所有者/管理员（对齐 {@link BfNoteServiceImpl} 的访问控制）。
 */
@Slf4j
@Service
public class BfNoteMediaServiceImpl implements BfNoteMediaService {

    /** 媒体文件大小上限：20MB（图片/短录音/画画均远小于此） */
    private static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    /** 批量接口单次最多媒体数 */
    private static final int BATCH_MAX_IDS = 10;

    /** 批量接口跳过的大文件阈值（base64 会膨胀内存与响应，大文件客户端回退单个流式下载） */
    private static final long BATCH_MAX_BYTES = 4L * 1024 * 1024;

    /** 允许的 MIME 白名单 */
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif",
            "audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/3gpp", "audio/ogg", "audio/wav");

    private static final Set<String> MEDIA_TYPES = Set.of("IMAGE", "AUDIO", "DRAWING");

    /** MIME → 磁盘扩展名（上传与读取共用，保证文件名一致） */
    private static final Map<String, String> MIME_EXT = Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/webp", "webp", "image/gif", "gif",
            "audio/mpeg", "mp3", "audio/mp4", "m4a", "audio/x-m4a", "m4a",
            "audio/3gpp", "3gp", "audio/ogg", "ogg", "audio/wav", "wav");

    @Autowired
    private BfNoteMediaMapper mediaMapper;
    @Autowired
    private BaiflowProperties properties;
    @Autowired
    private I18nUtil i18nUtil;

    @Override
    @Transactional
    public NoteMediaInfo upload(String userId, MultipartFile file, String mediaType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "笔记媒体文件大小不能超过 20MB");
        }

        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime)) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "不支持的媒体格式，仅支持图片（png/jpeg/webp/gif）与音频");
        }
        String type = normalizeMediaType(mediaType, mime);

        BfNoteMedia media = new BfNoteMedia();
        media.setUserId(userId);
        media.setMediaType(NoteMediaType.valueOf(type));
        media.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "media");
        media.setMimeType(mime);
        media.setSizeBytes(file.getSize());
        mediaMapper.insert(media);

        Path dir = Path.of(properties.getStorage().getNoteMediaPath());
        Path target = dir.resolve(media.getId() + "." + extensionFor(mime)).normalize();
        if (!target.startsWith(dir.toAbsolutePath().normalize())) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, "非法的媒体存储路径");
        }
        try {
            Files.createDirectories(dir);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("笔记媒体保存失败: userId={}, mediaId={}", userId, media.getId(), e);
            throw new BusinessException(ErrorCode.FILE_OPERATION_FAILED, i18nUtil.translate("媒体保存失败：") + e.getMessage());
        }

        log.info("笔记媒体已上传: userId={}, mediaId={}, type={}", userId, media.getId(), type);
        return NoteMediaInfo.from(media);
    }

    @Override
    public MediaResource load(String mediaId, String userId, boolean isAdmin) {
        BfNoteMedia media = mediaMapper.selectById(mediaId);
        if (media == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "媒体不存在");
        }
        if (!isAdmin && !userId.equals(media.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问此媒体");
        }

        Path dir = Path.of(properties.getStorage().getNoteMediaPath());
        Path target = dir.resolve(media.getId() + "." + extensionFor(media.getMimeType())).normalize();
        if (!target.startsWith(dir.toAbsolutePath().normalize()) || !Files.exists(target)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "媒体文件不存在");
        }
        return new MediaResource(media, target.toFile());
    }

    @Override
    public Map<String, String> batchBase64(String userId, boolean isAdmin, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        if (ids.size() > BATCH_MAX_IDS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "单次批量最多 " + BATCH_MAX_IDS + " 个媒体");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            BfNoteMedia media = mediaMapper.selectById(id);
            // 不存在/越权跳过：客户端对该 id 回退单个下载（单个接口会返回 404/403 明确错误）
            if (media == null || (!isAdmin && !userId.equals(media.getUserId()))) {
                continue;
            }
            Path dir = Path.of(properties.getStorage().getNoteMediaPath());
            Path target = dir.resolve(media.getId() + "." + extensionFor(media.getMimeType())).normalize();
            if (!target.startsWith(dir.toAbsolutePath().normalize()) || !Files.exists(target)) {
                continue;
            }
            // 大文件不批量（base64 内存/响应膨胀），回退单个流式下载
            try {
                if (Files.size(target) > BATCH_MAX_BYTES) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(target);
                result.put(id, Base64.getEncoder().encodeToString(bytes));
            } catch (IOException e) {
                log.warn("批量媒体读取失败: mediaId={}", id, e);
            }
        }
        return result;
    }

    // ---- 私有辅助 ----

    /** 归一化媒体类型：优先用客户端声明的类型并校验与 MIME 匹配，否则按 MIME 推断 */
    private String normalizeMediaType(String mediaType, String mime) {
        boolean audio = mime.startsWith("audio/");
        boolean image = mime.startsWith("image/");
        if (mediaType != null && !mediaType.isBlank()) {
            String t = mediaType.trim().toUpperCase();
            if (!MEDIA_TYPES.contains(t)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无效的媒体类型");
            }
            if ("AUDIO".equals(t) && !audio) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "媒体类型与文件格式不匹配");
            }
            if (("IMAGE".equals(t) || "DRAWING".equals(t)) && !image) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "媒体类型与文件格式不匹配");
            }
            return t;
        }
        if (audio) return "AUDIO";
        if (image) return "IMAGE";
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的媒体格式");
    }

    /** MIME → 磁盘扩展名，默认 bin */
    private String extensionFor(String mime) {
        return MIME_EXT.getOrDefault(mime != null ? mime : "", "bin");
    }
}
