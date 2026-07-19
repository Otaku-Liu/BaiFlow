package com.baiflow.share.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.file.dto.response.FileItemInfo;
import com.baiflow.share.dto.request.VerifyCodeRequest;
import com.baiflow.share.dto.response.ShareLinkInfo;
import com.baiflow.share.service.ShareService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 公开分享访问接口 — 无需登录，通过 share token 和 URL 路径访问 */
@RestController
@RequestMapping("/api/public/shares")
public class PublicShareController {
    private final ShareService shareService;
    public PublicShareController(ShareService s) { this.shareService = s; }

    /** 查看分享元信息 */
    @GetMapping("/{token}")
    public ApiResponse<ShareLinkInfo> view(@PathVariable String token, HttpServletRequest request) {
        return ApiResponse.success(shareService.viewByToken(token, request));
    }

    /** 校验提取码 */
    @PostMapping("/{token}/verify-code")
    public ApiResponse<Map<String,Object>> verifyCode(@PathVariable String token,
                                                       @Valid @RequestBody VerifyCodeRequest req,
                                                       HttpServletRequest request) {
        return ApiResponse.success(shareService.verifyExtractionCode(token, req.extractionCode(), request));
    }

    /** 校验隐私文件夹密码 */
    @PostMapping("/{token}/verify-private-password")
    public ApiResponse<Map<String,Object>> verifyPrivatePassword(@PathVariable String token,
                                                                  @RequestBody Map<String,String> body,
                                                                  HttpServletRequest request) {
        return ApiResponse.success(shareService.verifyPrivatePassword(token,
                body.getOrDefault("password",""), request));
    }

    /** 浏览分享文件夹 */
    @GetMapping("/{token}/files")
    public ApiResponse<IPage<FileItemInfo>> browseFolder(@PathVariable String token,
                                                          @RequestParam(required = false) String parentId,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "50") int size,
                                                          HttpServletRequest request) {
        return ApiResponse.success(shareService.browseShareFolder(token, parentId, page, size, null, request));
    }

    /** 下载分享文件 */
    @GetMapping("/{token}/download")
    public ApiResponse<Map<String,String>> download(@PathVariable String token,
                                                     @RequestParam String fileId,
                                                     HttpServletRequest request) {
        // 返回文件 URL 或直接流式传输
        // MVP：返回可用的下载 token 信息，前端构造下载链接
        shareService.downloadShareFile(token, fileId, null, request);
        // 实际流式下载在前端通过 /api/files/download 完成（需要拿到临时令牌）
        // 简化：返回提示信息
        return ApiResponse.success(Map.of("message","下载功能已记录，请通过文件ID下载"));
    }
}
