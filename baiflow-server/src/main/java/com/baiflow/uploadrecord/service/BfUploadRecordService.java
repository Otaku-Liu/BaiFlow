package com.baiflow.uploadrecord.service;

import com.baiflow.uploadrecord.dto.response.UploadRecordInfo;
import com.baiflow.uploadrecord.entity.BfUploadRecord;
import com.baiflow.uploadrecord.enums.UploadSource;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;

/**
 * 上传记录服务 — 记录上传动作、分页查询上传历史（含过滤与角色限定）。
 */
public interface BfUploadRecordService extends IService<BfUploadRecord> {

    /**
     * 记录一次文件上传（异步写入，不阻塞上传响应）。
     *
     * @param fileId         上传的文件 ID
     * @param fileName       文件名快照
     * @param uploaderUserId 上传人用户 ID
     * @param source         来源客户端（WEB / ANDROID）
     * @param ip             上传 IP
     * @param userAgent      上传 User-Agent
     */
    void recordUpload(String fileId, String fileName, String uploaderUserId,
                      UploadSource source, String ip, String userAgent);

    /**
     * 分页查询上传历史（按时间倒序，附上传人用户名）。
     *
     * @param currentUserId 当前登录用户 ID（非 admin 只看自己）
     * @param isAdmin       是否管理员（admin 可查全部/指定用户）
     * @param targetUserId  admin 指定的目标用户 ID（null = 全部；非 admin 忽略）
     * @param start         起始日期（含当日）
     * @param end           结束日期（含当日）
     * @param fileName      文件名模糊
     * @param source        来源精确（WEB/ANDROID；null = 全部）
     * @param page          页码（从 1 开始）
     * @param size          每页数量
     */
    IPage<UploadRecordInfo> pageHistory(String currentUserId, boolean isAdmin, String targetUserId,
                                        LocalDate start, LocalDate end, String fileName,
                                        String source, int page, int size);
}
