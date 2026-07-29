package com.baiflow.share.controller;

import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.entity.ApiResponse;
import com.baiflow.share.dto.request.CreateShareRequest;
import com.baiflow.share.dto.request.UpdateShareRequest;
import com.baiflow.share.dto.response.ShareLinkInfo;
import com.baiflow.share.entity.ShareAccessLog;
import com.baiflow.share.service.ShareService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 分享管理接口 — 需要登录，管理员可管理全部 */
@RestController
@RequestMapping("/api/shares")
public class ShareController {
    @Autowired
    private ShareService shareService;

    @PostMapping
    public ApiResponse<ShareLinkInfo> create(@Valid @RequestBody CreateShareRequest req, Authentication auth) {
        return ApiResponse.success(shareService.createShare(req, auth.getPrincipal().toString()));
    }

    @GetMapping
    public ApiResponse<IPage<ShareLinkInfo>> list(@RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   Authentication auth) {
        return ApiResponse.success(shareService.listShares(auth.getPrincipal().toString(), isAdmin(auth), status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ShareLinkInfo> get(@PathVariable String id, Authentication auth) {
        return ApiResponse.success(shareService.getShare(id, auth.getPrincipal().toString(), isAdmin(auth)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ShareLinkInfo> update(@PathVariable String id, @RequestBody UpdateShareRequest req, Authentication auth) {
        return ApiResponse.success(shareService.updateShare(id, req, auth.getPrincipal().toString(), isAdmin(auth)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String,Object>> revoke(@PathVariable String id, Authentication auth) {
        shareService.revokeShare(id, auth.getPrincipal().toString(), isAdmin(auth));
        return ApiResponse.success(Map.of("result","已撤销"));
    }

    /** 查询分享链接访问日志 — 仅管理员 */
    @GetMapping("/{id}/analytics")
    public ApiResponse<IPage<ShareAccessLog>> analytics(@PathVariable String id,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         Authentication auth) {
        if (!isAdmin(auth)) {
            return ApiResponse.error(ErrorCode.FORBIDDEN, "仅管理员可查看");
        }
        return ApiResponse.success(shareService.getShareAnalytics(id, page, size));
    }

    private boolean isAdmin(Authentication a) { return a.getAuthorities().stream().anyMatch(g->g.getAuthority().equals("ROLE_ADMIN")); }
}
