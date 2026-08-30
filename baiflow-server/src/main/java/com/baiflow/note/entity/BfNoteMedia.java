package com.baiflow.note.entity;

import com.baiflow.note.enums.NoteMediaType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 随手记笔记媒体实体 — 图片/录音/画画的元数据。
 * <p>
 * 媒体文件本体落磁盘（{@code baiflow.notes.media-path} 专用目录，文件名为
 * {@code <mediaId>.<ext>}），本表只存元数据；独立于文件中心，不参与 /api/files 列表。
 * 笔记正文通过 Markdown 引用（如 {@code ![名称](/api/notes/media/{id})}）关联媒体。
 */
@Data
@TableName("bf_note_media")
public class BfNoteMedia {
    /** 主键，UUID 自动生成（也是磁盘文件名主体） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所有者用户 ID */
    private String userId;

    /** 媒体类型：IMAGE / AUDIO / DRAWING */
    private NoteMediaType mediaType;

    /** 原始文件名 */
    private String fileName;

    /** Content-Type */
    private String mimeType;

    /** 文件大小（字节） */
    private Long sizeBytes;

    private LocalDateTime createdAt;
}
