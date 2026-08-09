package com.baiflow.downloadrecord.service;

import com.baiflow.downloadrecord.dto.response.DownloadRecordInfo;
import com.baiflow.downloadrecord.entity.DownloadRecord;
import com.baiflow.downloadrecord.enums.DownloadSource;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 下载记录服务 — 记录下载动作、统计文件下载次数、查询下载明细。
 */
public interface DownloadRecordService extends IService<DownloadRecord> {

    /**
     * 记录一次文件下载（异步写入，不阻塞下载响应）。
     *
     * @param fileId           被下载的文件 ID
     * @param fileName         文件名快照
     * @param downloaderUserId 下载人用户 ID（分享匿名下载传 null）
     * @param source           来源（CLIENT / SHARE）
     * @param shareId          分享链接 ID（非分享下载传 null）
     * @param ip               下载 IP
     * @param userAgent        下载 User-Agent
     */
    void recordDownload(String fileId, String fileName, String downloaderUserId,
                        DownloadSource source, String shareId, String ip, String userAgent);

    /**
     * 统计单个文件的下载次数（CLIENT + SHARE 均计入）。
     */
    long countByFileId(String fileId);

    /**
     * 批量统计多个文件的下载次数，返回 fileId → 次数。
     */
    Map<String, Long> countByFileIds(List<String> fileIds);

    /**
     * 分页查询某文件的下载明细（按时间倒序，附下载人用户名）。
     */
    IPage<DownloadRecordInfo> pageByFileId(String fileId, int page, int size);
}
