package com.baiflow.downloadrecord.service.impl;

import com.baiflow.downloadrecord.dto.response.DownloadRecordInfo;
import com.baiflow.downloadrecord.entity.BfDownloadRecord;
import com.baiflow.downloadrecord.enums.DownloadSource;
import com.baiflow.downloadrecord.mapper.BfDownloadRecordMapper;
import com.baiflow.downloadrecord.service.BfDownloadRecordService;
import com.baiflow.user.mapper.BfUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 下载记录服务实现。
 */
@Service
public class BfDownloadRecordServiceImpl extends ServiceImpl<BfDownloadRecordMapper, BfDownloadRecord>
        implements BfDownloadRecordService {

    @Autowired
    private BfUserMapper userMapper;

    @Override
    @Async
    public void recordDownload(String fileId, String fileName, String downloaderUserId,
                               DownloadSource source, String shareId, String ip, String userAgent) {
        BfDownloadRecord r = new BfDownloadRecord();
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
        return count(new LambdaQueryWrapper<BfDownloadRecord>().eq(BfDownloadRecord::getFileId, fileId));
    }

    @Override
    public Map<String, Long> countByFileIds(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        return list(new LambdaQueryWrapper<BfDownloadRecord>()
                        .select(BfDownloadRecord::getFileId)
                        .in(BfDownloadRecord::getFileId, fileIds))
                .stream()
                .collect(Collectors.groupingBy(BfDownloadRecord::getFileId, Collectors.counting()));
    }

    @Override
    public IPage<DownloadRecordInfo> pageByFileId(String fileId, int page, int size) {
        IPage<BfDownloadRecord> p = page(new Page<>(page, size), new LambdaQueryWrapper<BfDownloadRecord>()
                .eq(BfDownloadRecord::getFileId, fileId)
                .orderByDesc(BfDownloadRecord::getCreatedAt));
        // 解析下载人用户名（分享匿名为空）
        Set<String> userIds = p.getRecords().stream()
                .map(BfDownloadRecord::getDownloaderUserId)
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

    @Override
    public IPage<DownloadRecordInfo> pageHistory(String currentUserId, boolean isAdmin, String targetUserId,
                                                 LocalDate start, LocalDate end, String fileName,
                                                 String source, int page, int size) {
        DownloadSource sourceEnum = parseSource(source);
        // 先算好时间边界再进条件（避免 null 时在条件内急切求值 NPE）
        LocalDateTime startTime = start != null ? start.atStartOfDay() : null;
        LocalDateTime endTime = end != null ? end.plusDays(1).atStartOfDay() : null;
        IPage<BfDownloadRecord> p = page(new Page<>(page, size), new LambdaQueryWrapper<BfDownloadRecord>()
                .eq(!isAdmin, BfDownloadRecord::getDownloaderUserId, currentUserId)
                .eq(isAdmin && targetUserId != null && !targetUserId.isBlank(),
                        BfDownloadRecord::getDownloaderUserId, targetUserId)
                .ge(startTime != null, BfDownloadRecord::getCreatedAt, startTime)
                .lt(endTime != null, BfDownloadRecord::getCreatedAt, endTime)
                .like(fileName != null && !fileName.isBlank(), BfDownloadRecord::getFileName, fileName)
                .eq(sourceEnum != null, BfDownloadRecord::getSource, sourceEnum)
                .orderByDesc(BfDownloadRecord::getCreatedAt));
        // 解析下载人用户名（分享匿名为空）
        Set<String> userIds = p.getRecords().stream()
                .map(BfDownloadRecord::getDownloaderUserId)
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

    /** 解析来源字符串为枚举；非法/空返回 null（不过滤） */
    private static DownloadSource parseSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return DownloadSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
