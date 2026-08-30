package com.baiflow.file.controller;

import com.baiflow.common.entity.ApiResponse;
import com.baiflow.downloadrecord.dto.response.DownloadRecordInfo;
import com.baiflow.downloadrecord.enums.DownloadSource;
import com.baiflow.downloadrecord.service.BfDownloadRecordService;
import com.baiflow.file.dto.request.CreateFolderRequest;
import com.baiflow.file.dto.request.MoveRequest;
import com.baiflow.file.dto.request.RenameRequest;
import com.baiflow.file.dto.request.SetPrivacyRequest;
import com.baiflow.file.dto.request.VerifyPrivacyRequest;
import com.baiflow.file.dto.response.FileItemInfo;
import com.baiflow.file.service.BfFileItemService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
public class BfFileItemController {

    @Autowired
    private BfFileItemService fileService;
    @Autowired
    private BfDownloadRecordService downloadRecordService;

    /**
     * 列出指定存储根目录或文件夹下的子文件/子目录（目录优先排序）。
     * 进入隐私文件夹时需要 X-Privacy-Access-Token 头。
     */
    @GetMapping
    public ApiResponse<IPage<FileItemInfo>> list(@RequestParam String storageRootId,
                                                  @RequestParam(required = false) String parentId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "50") int size,
                                                  @RequestParam(required = false) String viewUserId,
                                                  @RequestParam(defaultValue = "name") String sort,
                                                  @RequestParam(required = false) String dir,
                                                  @RequestHeader(value = "X-Privacy-Access-Token",
                                                          required = false) String privacyAccessToken,
                                                  Authentication auth) {
        return ApiResponse.success(
                fileService.listFiles(storageRootId, parentId, page, size,
                        auth.getPrincipal().toString(), isAdmin(auth), privacyAccessToken, viewUserId, sort, dir));
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
                                             @RequestParam(required = false) String viewUserId,
                                             Authentication auth) {
        String userId = auth.getPrincipal().toString();
        String effectiveUserId = isAdmin(auth) && viewUserId != null ? viewUserId : userId;
        return ApiResponse.success(
                fileService.uploadFile(storageRootId, parentId, file,
                        userId, effectiveUserId, privacyAccessToken));
    }

    /**
     * 计算文件/文件夹大小：文件返回自身字节数，文件夹递归汇总子树内文件字节数。
     * 隐私文件夹内目标需要 X-Privacy-Access-Token 头。
     */
    @GetMapping("/{id}/size")
    public ApiResponse<Long> size(@PathVariable String id,
                                  @RequestHeader(value = "X-Privacy-Access-Token",
                                          required = false) String privacyAccessToken,
                                  Authentication auth) {
        return ApiResponse.success(fileService.computeSize(id, auth.getPrincipal().toString(),
                isAdmin(auth), privacyAccessToken));
    }

    /**
     * 根据文件 ID 流式下载文件。
     * 文件在隐私文件夹内时需要 X-Privacy-Access-Token 头。
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId,
                                              @RequestHeader(value = "X-Privacy-Access-Token",
                                                      required = false) String privacyAccessToken,
                                              Authentication auth, HttpServletRequest request) {
        Resource r = fileService.downloadFile(fileId, auth.getPrincipal().toString(),
                isAdmin(auth), privacyAccessToken);
        String fn = r.getFilename() != null ? r.getFilename() : "download";
        // 记录一次登录用户直接下载（异步写入），供文件中心下载次数统计与审计
        downloadRecordService.recordDownload(fileId, fn, auth.getPrincipal().toString(),
                DownloadSource.CLIENT, null,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fn, StandardCharsets.UTF_8).build().toString())
                .body(r);
    }

    /**
     * 分页查询某文件的下载记录（本人文件；管理员可查任意）— 供文件中心「下载详情」。
     */
    @GetMapping("/{id}/downloads")
    public ApiResponse<IPage<DownloadRecordInfo>> listDownloads(@PathVariable String id,
                                                                 @RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 Authentication auth) {
        return ApiResponse.success(fileService.listFileDownloads(id, auth.getPrincipal().toString(),
                isAdmin(auth), page, size));
    }

    /**
     * 在指定存储根目录下创建新文件夹。
     * 在隐私文件夹内创建时需要 X-Privacy-Access-Token 头。
     */
    @PostMapping("/folders")
    public ApiResponse<FileItemInfo> createFolder(@Valid @RequestBody CreateFolderRequest req,
                                                   @RequestHeader(value = "X-Privacy-Access-Token",
                                                           required = false) String privacyAccessToken,
                                                   @RequestParam(required = false) String viewUserId,
                                                   Authentication auth) {
        String userId = auth.getPrincipal().toString();
        String effectiveUserId = isAdmin(auth) && viewUserId != null ? viewUserId : userId;
        return ApiResponse.success(
                fileService.createFolder(req, userId, effectiveUserId, privacyAccessToken));
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

    /**
     * 以 inline 模式流式返回文件，供浏览器预览（图片/视频/PDF 等）。
     * 支持 Range 请求头（视频拖拽 seek）。
     */
    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable String id,
                                            @RequestHeader(value = "X-Privacy-Access-Token",
                                                    required = false) String privacyAccessToken,
                                            Authentication auth) {
        Resource r = fileService.previewFile(id, auth.getPrincipal().toString(),
                isAdmin(auth), privacyAccessToken);
        String fn = r.getFilename() != null ? r.getFilename() : "preview";
        MediaType mediaType = resolveMediaType(fn);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(fn, StandardCharsets.UTF_8).build().toString())
                .body(r);
    }

    /** 查询当前用户对某文件的播放/阅读进度 */
    @GetMapping("/{id}/progress")
    public ApiResponse<Map<String, Object>> getProgress(@PathVariable String id, Authentication auth) {
        Map<String, Object> progress = fileService.getProgress(id, auth.getPrincipal().toString());
        return ApiResponse.success(progress);
    }

    /** 保存播放/阅读进度 */
    @PutMapping("/{id}/progress")
    public ApiResponse<Map<String, Object>> saveProgress(@PathVariable String id,
                                                          @RequestBody Map<String, Object> body,
                                                          Authentication auth) {
        String positionType = body.get("positionType") != null ? body.get("positionType").toString() : "SECONDS";
        Double positionValue = body.get("positionValue") != null
                ? Double.parseDouble(body.get("positionValue").toString()) : 0;
        fileService.saveProgress(id, auth.getPrincipal().toString(), positionType, positionValue);
        return ApiResponse.success(Map.of("result", "ok"));
    }

    /** 根据文件名扩展名推断 MediaType，避免浏览器因 unknown 类型触发下载 */
    private MediaType resolveMediaType(String filename) {
        if (filename == null) return MediaType.APPLICATION_OCTET_STREAM;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))  return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".png"))  return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif"))  return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".svg"))  return MediaType.parseMediaType("image/svg+xml");
        if (lower.endsWith(".bmp"))  return MediaType.parseMediaType("image/bmp");
        if (lower.endsWith(".ico"))  return MediaType.parseMediaType("image/x-icon");
        if (lower.endsWith(".mp4"))  return MediaType.parseMediaType("video/mp4");
        if (lower.endsWith(".webm")) return MediaType.parseMediaType("video/webm");
        if (lower.endsWith(".ogv") || lower.endsWith(".ogg")) return MediaType.parseMediaType("video/ogg");
        if (lower.endsWith(".mp3"))  return MediaType.parseMediaType("audio/mpeg");
        if (lower.endsWith(".wav"))  return MediaType.parseMediaType("audio/wav");
        if (lower.endsWith(".flac")) return MediaType.parseMediaType("audio/flac");
        if (lower.endsWith(".m4a"))  return MediaType.parseMediaType("audio/mp4");
        if (lower.endsWith(".docx") || lower.endsWith(".doc"))
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls"))
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        if (lower.endsWith(".pptx") || lower.endsWith(".ppt"))
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        if (lower.endsWith(".odt")) return MediaType.parseMediaType("application/vnd.oasis.opendocument.text");
        if (lower.endsWith(".ods")) return MediaType.parseMediaType("application/vnd.oasis.opendocument.spreadsheet");
        if (lower.endsWith(".odp")) return MediaType.parseMediaType("application/vnd.oasis.opendocument.presentation");
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json")
                || lower.endsWith(".xml") || lower.endsWith(".csv") || lower.endsWith(".html")
                || lower.endsWith(".css") || lower.endsWith(".js") || lower.endsWith(".ts")
                || lower.endsWith(".py") || lower.endsWith(".java") || lower.endsWith(".log")
                || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".sh")) {
            return MediaType.TEXT_PLAIN;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private boolean isAdmin(Authentication a) {
        return a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
    }
}
