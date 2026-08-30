package com.baiflow.uploadrecord.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.uploadrecord.dto.response.UploadRecordInfo;
import com.baiflow.uploadrecord.service.BfUploadRecordService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 上传记录查询接口 — 非 admin 只看自己的上传历史；admin 可传 userId 查看任意用户。
 * 过滤：时间范围（start/end 日期，含端点日）、文件名模糊、来源（WEB/ANDROID）。
 */
@RestController
@RequestMapping("/api/upload-records")
public class BfUploadRecordController {

    @Autowired
    private BfUploadRecordService uploadRecordService;

    @GetMapping
    public ApiResponse<IPage<UploadRecordInfo>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        String currentUserId = auth.getPrincipal().toString();
        boolean isAdmin = isAdmin(auth);
        return ApiResponse.success(uploadRecordService.pageHistory(
                currentUserId, isAdmin, isAdmin ? userId : null,
                start, end, fileName, source, page, size));
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(ga.getAuthority())) return true;
        }
        return false;
    }
}
