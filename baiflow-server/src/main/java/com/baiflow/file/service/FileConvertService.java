package com.baiflow.file.service;

import com.baiflow.file.entity.FileItem;
import com.baiflow.storage.entity.StorageRoot;
import com.baiflow.storage.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Office 文件 → PDF 转换服务。
 * <p>通过调用系统中安装的 LibreOffice headless 将 doc/docx/ppt/pptx/xls/xlsx/odt/ods/odp
 * 转为 PDF，转换结果缓存于源文件同目录下，文件名 = 原文件名 + ".pdf"。</p>
 *
 * <p>缓存策略：如果 PDF 已存在且比源文件新，则跳过转换；否则重新生成。</p>
 */
@Slf4j
@Service
public class FileConvertService {

    /** 需要转换为 PDF 的 Office MIME 类型 */
    private static final Set<String> OFFICE_MIMES = Set.of(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation"
    );

    @Autowired
    private StorageService storageService;

    /** 判断文件是否需要转换为 PDF */
    public boolean needsConversion(FileItem file) {
        return file.getMimeType() != null && OFFICE_MIMES.contains(file.getMimeType());
    }

    /**
     * 将 Office 文件转为 PDF，返回 PDF 文件路径。
     * 如果缓存有效则直接返回缓存路径；否则调用 LibreOffice 转换。
     *
     * @return PDF 文件的 Path，转换失败返回 null
     */
    public Path convertToPdf(FileItem file) {
        StorageRoot root = storageService.getByIdOrThrow(file.getStorageRootId());
        Path srcPath = storageService.resolveRootPath(root)
                .resolve(file.getRelativePath()).normalize();
        storageService.verifyPathInRoot(root, srcPath);

        // LibreOffice 输出 PDF 会替换扩展名为 .pdf（如 report.docx → report.pdf）
        String baseName = file.getName().contains(".")
                ? file.getName().substring(0, file.getName().lastIndexOf('.'))
                : file.getName();
        Path pdfPath = srcPath.resolveSibling(baseName + ".pdf");

        // 缓存命中
        if (Files.exists(pdfPath)) {
            try {
                if (Files.getLastModifiedTime(pdfPath).compareTo(
                        Files.getLastModifiedTime(srcPath)) >= 0) {
                    log.debug("PDF 缓存命中: {}", pdfPath);
                    return pdfPath;
                }
            } catch (IOException e) {
                log.warn("读取文件时间失败，重新转换: {}", e.getMessage());
            }
        }

        // 调用 LibreOffice
        Path outDir = srcPath.getParent();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "libreoffice", "--headless", "--convert-to", "pdf",
                    "--outdir", outDir.toString(),
                    srcPath.toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                log.error("LibreOffice 转换超时: {}", srcPath);
                return null;
            }
            if (p.exitValue() != 0) {
                String err = new String(p.getInputStream().readAllBytes());
                log.error("LibreOffice 转换失败 (exit={}): {}", p.exitValue(), err);
                return null;
            }
            log.info("Office→PDF 转换成功: {} → {}", file.getRelativePath(), pdfPath);
            return pdfPath;
        } catch (IOException | InterruptedException e) {
            log.error("LibreOffice 转换异常: {}", e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }
}
