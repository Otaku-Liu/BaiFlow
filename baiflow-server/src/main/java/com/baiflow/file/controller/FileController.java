package com.baiflow.file.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.file.dto.request.CreateFolderRequest;
import com.baiflow.file.dto.request.MoveRequest;
import com.baiflow.file.dto.request.RenameRequest;
import com.baiflow.file.dto.request.SetPrivacyRequest;
import com.baiflow.file.dto.request.VerifyPrivacyRequest;
import com.baiflow.file.dto.response.FileItemInfo;
import com.baiflow.file.service.FileService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 文件管理接口控制器 — 处理文件浏览、上传、下载、文件夹创建、重命名、移动、删除和隐私文件夹管理。
 * <p>
 * 所有接口要求认证。非 ADMIN 用户需通过 {@code user_storage_permission} 校验存储访问权限。
 * 隐私文件夹（PRIVATE 模式）要求提供 {@code X-Privacy-Access-Token} 头，
 * 通过 {@code POST /api/files/{id}/privacy/verify} 获取短期访问令牌后可免重复输入密码。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) { this.fileService = fileService; }

    /**
     * 列出指定存储根目录或文件夹下的子文件/子目录（目录优先排序）。
     * 进入隐私文件夹时需要 X-Privacy-Access-Token 头。
     */
    @GetMapping
    public ApiResponse<IPage<FileItemInfo>> list(@RequestParam String storageRootId,
                                                  @RequestParam(required = false) String parentId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "50") int size,
                                                  @RequestHeader(value = "X-Privacy-Access-Token",
                                                          required = false) String privacyAccessToken,
                                                  Authentication auth) {
        return ApiResponse.success(
                fileService.listFiles(storageRootId, parentId, page, size,
                        auth.getPrincipal().toString(), isAdmin(auth), privacyAccessToken));
    }

    /**
     * 上传文件到指定存储根目录和父文件夹。
     * 目标为隐私文件夹时需要 X-Privacy-Access-Token 头。
     */
    @PostMapping("/upload")
    public ApiResponse<FileItemInfo> upload(@RequestParam String storageRootId,
                                             @RequestParam(required = false) String parentId,
                                             @RequestParam("file") MultipartFile file,
                                             @RequestHeader(value = "X-Privacy-Access-Token",
                                                     required = false) String privacyAccessToken,
                                             Authentication auth) {
        return ApiResponse.success(
                fileService.uploadFile(storageRootId, parentId, file,
                        auth.getPrincipal().toString(), privacyAccessToken));
    }

    /**
     * 根据文件 ID 流式下载文件。
     * 文件在隐私文件夹内时需要 X-Privacy-Access-Token 头。
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId,
                                              @RequestHeader(value = "X-Privacy-Access-Token",
                                                      required = false) String privacyAccessToken,
                                              Authentication auth) {
        Resource r = fileService.downloadFile(fileId, auth.getPrincipal().toString(),
                isAdmin(auth), privacyAccessToken);
        String fn = r.getFilename() != null ? r.getFilename() : "download";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fn, StandardCharsets.UTF_8).build().toString())
                .body(r);
    }

    /**
     * 在指定存储根目录下创建新文件夹。
     * 在隐私文件夹内创建时需要 X-Privacy-Access-Token 头。
     */
    @PostMapping("/folders")
    public ApiResponse<FileItemInfo> createFolder(@Valid @RequestBody CreateFolderRequest req,
                                                   @RequestHeader(value = "X-Privacy-Access-Token",
                                                           required = false) String privacyAccessToken,
                                                   Authentication auth) {
        return ApiResponse.success(
                fileService.createFolder(req, auth.getPrincipal().toString(), privacyAccessToken));
    }

    /**
     * 重命名文件或文件夹。
     * 目标在隐私文件夹内时需要 X-Privacy-Access-Token 头。
     */
    @PatchMapping("/{id}/rename")
    public ApiResponse<FileItemInfo> rename(@PathVariable String id,
                                             @Valid @RequestBody RenameRequest req,
                                             @RequestHeader(value = "X-Privacy-Access-Token",
                                                     required = false) String privacyAccessToken,
                                             Authentication auth) {
        return ApiResponse.success(
                fileService.rename(id, req, auth.getPrincipal().toString(), isAdmin(auth), privacyAccessToken));
    }

    /**
     * 将文件或文件夹移动到其他存储根目录或父文件夹。
     * 源在隐私文件夹内时需要 X-Privacy-Access-Token 头。
     */
    @PatchMapping("/{id}/move")
    public ApiResponse<FileItemInfo> move(@PathVariable String id,
                                           @Valid @RequestBody MoveRequest req,
                                           @RequestHeader(value = "X-Privacy-Access-Token",
                                                   required = false) String privacyAccessToken,
                                           Authentication auth) {
        return ApiResponse.success(
                fileService.move(id, req, auth.getPrincipal().toString(), isAdmin(auth), privacyAccessToken));
    }

    /**
     * 软删除文件或文件夹。
     * 目标在隐私文件夹内时需要 X-Privacy-Access-Token 头。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id,
                                                    @RequestHeader(value = "X-Privacy-Access-Token",
                                                            required = false) String privacyAccessToken,
                                                    Authentication auth) {
        fileService.delete(id, auth.getPrincipal().toString(), isAdmin(auth), privacyAccessToken);
        return ApiResponse.success(Map.of("result", "已删除"));
    }

    /**
     * 为文件夹设置隐私密码，将其标记为 PRIVATE 模式。
     * 仅目录类型支持，密码使用 BCrypt 哈希后存储。
     * 设置后已有访问会话立即失效。
     */
    @PostMapping("/{id}/privacy")
    public ApiResponse<FileItemInfo> setPrivacy(@PathVariable String id,
                                                 @Valid @RequestBody SetPrivacyRequest req,
                                                 Authentication auth) {
        return ApiResponse.success(
                fileService.setPrivacy(id, req, auth.getPrincipal().toString()));
    }

    /**
     * 取消文件夹的隐私保护，恢复为 NORMAL 模式。
     * 同时清除隐私密码和所有访问会话。
     */
    @DeleteMapping("/{id}/privacy")
    public ApiResponse<FileItemInfo> removePrivacy(@PathVariable String id,
                                                    Authentication auth) {
        return ApiResponse.success(
                fileService.removePrivacy(id, auth.getPrincipal().toString()));
    }

    /**
     * 验证隐私文件夹密码，成功返回短期访问令牌。
     * 令牌有效期 30 分钟，通过 X-Privacy-Access-Token 头传给后续请求。
     */
    @PostMapping("/{id}/privacy/verify")
    public ApiResponse<Map<String, Object>> verifyPrivacy(@PathVariable String id,
                                                           @Valid @RequestBody VerifyPrivacyRequest req,
                                                           Authentication auth) {
        return ApiResponse.success(
                fileService.verifyPrivacy(id, req, auth.getPrincipal().toString()));
    }

    private boolean isAdmin(Authentication a) {
        return a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
    }
}
