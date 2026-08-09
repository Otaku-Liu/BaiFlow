package com.baiflow.downloadrecord.service.impl;

import com.baiflow.downloadrecord.dto.response.DownloadRecordInfo;
import com.baiflow.downloadrecord.entity.DownloadRecord;
import com.baiflow.downloadrecord.enums.DownloadSource;
import com.baiflow.downloadrecord.mapper.DownloadRecordMapper;
import com.baiflow.downloadrecord.service.DownloadRecordService;
import com.baiflow.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 下载记录服务实现。
 */
@Service
public class DownloadRecordServiceImpl extends ServiceImpl<DownloadRecordMapper, DownloadRecord>
        implements DownloadRecordService {

    @Autowired
    private UserMapper userMapper;

    @Override
    @Async
    public void recordDownload(String fileId, String fileName, String downloaderUserId,
                               DownloadSource source, String shareId, String ip, String userAgent) {
        DownloadRecord r = new DownloadRecord();
        r.setFileId(fileId);
        r.setFileName(fileName != null ? fileName : "");
        r.setDownloaderUserId(downloaderUserId);
        r.setSource(source);
        r.setShareId(shareId);
        r.setIpAddress(ip != null ? ip : "");
        r.setUserAgent(userAgent != null ? userAgent : "");
        save(r);
    }

    @Override
    public long countByFileId(String fileId) {
        return count(new LambdaQueryWrapper<DownloadRecord>().eq(DownloadRecord::getFileId, fileId));
    }

    @Override
    public Map<String, Long> countByFileIds(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        return list(new LambdaQueryWrapper<DownloadRecord>()
                        .select(DownloadRecord::getFileId)
                        .in(DownloadRecord::getFileId, fileIds))
                .stream()
                .collect(Collectors.groupingBy(DownloadRecord::getFileId, Collectors.counting()));
    }

    @Override
    public IPage<DownloadRecordInfo> pageByFileId(String fileId, int page, int size) {
        IPage<DownloadRecord> p = page(new Page<>(page, size), new LambdaQueryWrapper<DownloadRecord>()
                .eq(DownloadRecord::getFileId, fileId)
                .orderByDesc(DownloadRecord::getCreatedAt));
        // 解析下载人用户名（分享匿名为空）
        Set<String> userIds = p.getRecords().stream()
                .map(DownloadRecord::getDownloaderUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> usernames = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> usernames.put(u.getId(), u.getUsername()));
        }
        IPage<DownloadRecordInfo> r = new Page<>(page, size, p.getTotal());
        r.setRecords(p.getRecords().stream()
                .map(rec -> DownloadRecordInfo.from(rec, usernames.get(rec.getDownloaderUserId())))
                .toList());
        return r;
    }
}
