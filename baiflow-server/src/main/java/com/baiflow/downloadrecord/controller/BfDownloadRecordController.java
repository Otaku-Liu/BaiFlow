package com.baiflow.downloadrecord.controller;

import com.baiflow.auth.security.SecurityUtils;
import com.baiflow.common.entity.ApiResponse;
import com.baiflow.downloadrecord.dto.response.DownloadRecordInfo;
import com.baiflow.downloadrecord.service.BfDownloadRecordService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 下载记录查询接口 — 非 admin 只看自己的 CLIENT 下载（分享匿名下载不可见）；admin 可传 userId 查看任意用户。
 * 过滤：时间范围（start/end 日期，含端点日）、文件名模糊、来源（CLIENT/SHARE）。
 */
@RestController
@RequestMapping("/api/download-records")
@RequiredArgsConstructor
public class BfDownloadRecordController {

    private final BfDownloadRecordService downloadRecordService;

    @GetMapping
    public ApiResponse<IPage<DownloadRecordInfo>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        String currentUserId = auth.getPrincipal().toString();
        boolean isAdmin = SecurityUtils.isAdmin(auth);
        return ApiResponse.success(downloadRecordService.pageHistory(
                currentUserId, isAdmin, isAdmin ? userId : null,
                start, end, fileName, source, page, size));
    }
}
