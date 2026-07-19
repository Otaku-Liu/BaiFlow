package com.baiflow.share.service;

import com.baiflow.share.dto.request.CreateShareRequest;
import com.baiflow.share.dto.request.UpdateShareRequest;
import com.baiflow.share.dto.response.ShareLinkInfo;
import com.baiflow.file.dto.response.FileItemInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import java.util.Map;

/**
 * 分享服务接口 — 分享链接的创建、管理、公开访问。
 */
public interface ShareService {

    /** 创建分享链接（管理员或文件所有者） */
    ShareLinkInfo createShare(CreateShareRequest req, String userId);

    /** 分页查询分享列表（管理员可查全部） */
    IPage<ShareLinkInfo> listShares(String userId, boolean isAdmin, String status, int page, int size);

    /** 查询分享详情 */
    ShareLinkInfo getShare(String id, String userId, boolean isAdmin);

    /** 更新分享（过期时间、次数、提取码、状态） */
    ShareLinkInfo updateShare(String id, UpdateShareRequest req, String userId, boolean isAdmin);

    /** 撤销分享 */
    void revokeShare(String id, String userId, boolean isAdmin);

    // ---- 公开访问接口 ----
    /** 查看分享元信息（token -> 分享详情） */
    ShareLinkInfo viewByToken(String token, HttpServletRequest request);

    /** 校验提取码 */
    Map<String, Object> verifyExtractionCode(String token, String code, HttpServletRequest request);

    /** 校验隐私文件夹密码 */
    Map<String, Object> verifyPrivatePassword(String token, String password, HttpServletRequest request);

    /** 浏览分享文件夹内的文件列表 */
    IPage<FileItemInfo> browseShareFolder(String token, String parentId, int page, int size, String privacyToken, HttpServletRequest request);

    /** 下载分享文件 */
    Resource downloadShareFile(String token, String fileId, String privacyToken, HttpServletRequest request);
}
