package com.baiflow.uploadrecord.service.impl;

import com.baiflow.uploadrecord.dto.response.UploadRecordInfo;
import com.baiflow.uploadrecord.entity.BfUploadRecord;
import com.baiflow.uploadrecord.enums.UploadSource;
import com.baiflow.uploadrecord.mapper.BfUploadRecordMapper;
import com.baiflow.uploadrecord.service.BfUploadRecordService;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 上传记录服务实现。
 */
@Service
public class BfUploadRecordServiceImpl extends ServiceImpl<BfUploadRecordMapper, BfUploadRecord>
        implements BfUploadRecordService {

    @Autowired
    private BfUserMapper userMapper;

    @Override
    @Async
    public void recordUpload(String fileId, String fileName, String uploaderUserId,
                             UploadSource source, String ip, String userAgent) {
        BfUploadRecord r = new BfUploadRecord();
        r.setFileId(fileId);
        r.setFileName(fileName != null ? fileName : "");
        r.setUploaderUserId(uploaderUserId);
        r.setSource(source);
        r.setIpAddress(ip != null ? ip : "");
        r.setUserAgent(userAgent != null ? userAgent : "");
        save(r);
    }

    @Override
    public IPage<UploadRecordInfo> pageHistory(String currentUserId, boolean isAdmin, String targetUserId,
                                               LocalDate start, LocalDate end, String fileName,
                                               String source, int page, int size) {
        UploadSource sourceEnum = parseSource(source);
        // 先算好时间边界再进条件（避免 null 时在条件内急切求值 NPE）
        LocalDateTime startTime = start != null ? start.atStartOfDay() : null;
        LocalDateTime endTime = end != null ? end.plusDays(1).atStartOfDay() : null;
        LambdaQueryWrapper<BfUploadRecord> wrapper = new LambdaQueryWrapper<BfUploadRecord>()
                .eq(!isAdmin, BfUploadRecord::getUploaderUserId, currentUserId)
                .eq(isAdmin && targetUserId != null && !targetUserId.isBlank(),
                        BfUploadRecord::getUploaderUserId, targetUserId)
                .ge(startTime != null, BfUploadRecord::getCreatedAt, startTime)
                .lt(endTime != null, BfUploadRecord::getCreatedAt, endTime)
                .like(fileName != null && !fileName.isBlank(), BfUploadRecord::getFileName, fileName)
                .eq(sourceEnum != null, BfUploadRecord::getSource, sourceEnum)
                .orderByDesc(BfUploadRecord::getCreatedAt);
        IPage<BfUploadRecord> p = page(new Page<>(page, size), wrapper);

        // 解析上传人用户名（供 admin 视图区分用户）
        Set<String> userIds = p.getRecords().stream()
                .map(BfUploadRecord::getUploaderUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> usernames = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> usernames.put(u.getId(), u.getUsername()));
        }
        IPage<UploadRecordInfo> r = new Page<>(page, size, p.getTotal());
        r.setRecords(p.getRecords().stream()
                .map(rec -> UploadRecordInfo.from(rec, usernames.get(rec.getUploaderUserId())))
                .toList());
        return r;
    }

    /** 解析来源字符串为枚举；非法/空返回 null（不过滤） */
    private static UploadSource parseSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return UploadSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
