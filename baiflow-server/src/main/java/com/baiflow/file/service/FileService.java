package com.baiflow.file.service;

import com.baiflow.file.dto.request.CreateFolderRequest;
import com.baiflow.file.dto.request.MoveRequest;
import com.baiflow.file.dto.request.RenameRequest;
import com.baiflow.file.dto.request.SetPrivacyRequest;
import com.baiflow.file.dto.request.VerifyPrivacyRequest;
import com.baiflow.file.dto.response.FileItemInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件服务接口 — 文件浏览、上传、下载、文件夹创建、重命名、移动、软删除的核心 API。
 * <p>
 * 所有方法确保以下安全约束：
 * <ul>
 *   <li>每次磁盘操作都在目标存储根目录范围内（路径穿越防护）。</li>
 *   <li>非 ADMIN 调用者必须对存储根目录持有 {@code user_storage_permission} 授权。</li>
 *   <li>元数据在磁盘操作成功后写入（软删除除外——为安全考虑先标记元数据再删磁盘）。</li>
 *   <li>隐私文件夹（PRIVATE 模式）要求提供有效的 {@code privacyAccessToken}，
 *       即使对 ADMIN 和已授权用户也不豁免。</li>
 * </ul>
 */
public interface FileService {

    /**
     * 列出指定存储根目录或文件夹下的子文件/子目录。目录排在文件前面。
     * <p>
     * 如果父文件夹或其上级目录链中存在隐私文件夹，则要求提供有效的隐私访问令牌。
     *
     * @param storageRootId      存储根目录 ID
     * @param parentId           父文件夹 ID（null 表示根目录）
     * @param page               页码（从 1 开始）
     * @param size               每页数量
     * @param userId             调用者 ID（用于权限校验）
     * @param isAdmin            调用者是否拥有 ROLE_ADMIN
     * @param privacyAccessToken 隐私访问令牌（可空，进入隐私文件夹时必须提供）
     * @return 分页文件项列表
     * @throws com.baiflow.common.exception.BusinessException FORBIDDEN 无访问权限
     */
    IPage<FileItemInfo> listFiles(String storageRootId, String parentId, int page, int size,
                                  String userId, boolean isAdmin, String privacyAccessToken);

    /**
     * 接收多部分文件上传，将文件写入存储根目录内的磁盘位置，计算 SHA-256 哈希，并持久化元数据。
     * <p>
     * 上传前会对原始文件名进行清洗（移除路径分隔符和空字节）。
     * 如果目标父文件夹链上有隐私文件夹，则要求提供有效的隐私访问令牌。
     *
     * @param storageRootId      目标存储根目录
     * @param parentId           目标父文件夹（null 表示根目录）
     * @param file               上传的文件
     * @param userId             调用者 ID（成为文件所有者）
     * @param privacyAccessToken 隐私访问令牌（可空）
     * @return 已存储文件的元数据
     * @throws com.baiflow.common.exception.BusinessException FILE_OPERATION_FAILED 同名文件已存在或磁盘写入失败
     */
    FileItemInfo uploadFile(String storageRootId, String parentId, MultipartFile file,
                            String userId, String privacyAccessToken);

    /**
     * 根据文件 ID 解析文件并作为 {@link Resource} 返回以供流式下载。
     * <p>
     * 返回前会校验解析后的绝对路径仍然在存储根目录范围内。
     * 如果文件所在目录链上有隐私文件夹，则要求提供有效的隐私访问令牌。
     *
     * @param fileId             文件项 ID
     * @param userId             调用者 ID
     * @param isAdmin            是否拥有 ROLE_ADMIN
     * @param privacyAccessToken 隐私访问令牌（可空）
     * @return 可读的文件资源
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND 元数据或磁盘文件不存在
     * @throws com.baiflow.common.exception.BusinessException FILE_OPERATION_FAILED 目标项是目录而非文件
     */
    Resource downloadFile(String fileId, String userId, boolean isAdmin, String privacyAccessToken);

    /**
     * 在存储根目录中创建文件夹（同时在磁盘和元数据中创建）。
     * <p>
     * 如果目标父文件夹链上有隐私文件夹，则要求提供有效的隐私访问令牌。
     *
     * @param req                存储根目录、父节点和文件夹名称
     * @param userId             调用者 ID（成为所有者）
     * @param privacyAccessToken 隐私访问令牌（可空）
     * @return 新文件夹的元数据
     */
    FileItemInfo createFolder(CreateFolderRequest req, String userId, String privacyAccessToken);

    /**
     * 重命名文件或文件夹，同步更新元数据和磁盘上的名称。
     * <p>
     * 新名称会被清洗，相对路径会重新计算。
     *
     * @param id                 文件项 ID
     * @param req                新名称
     * @param userId             调用者 ID
     * @param isAdmin            是否拥有 ROLE_ADMIN
     * @param privacyAccessToken 隐私访问令牌（可空）
     * @return 更新后的元数据
     */
    FileItemInfo rename(String id, RenameRequest req, String userId, boolean isAdmin,
                        String privacyAccessToken);

    /**
     * 将文件或文件夹移动到其他存储根目录或父文件夹。
     * <p>
     * 目标存储根目录和父节点均会校验，磁盘移动操作在元数据更新之前执行。
     *
     * @param id                 文件项 ID
     * @param req                目标存储根目录 ID 和目标父文件夹 ID
     * @param userId             调用者 ID
     * @param isAdmin            是否拥有 ROLE_ADMIN
     * @param privacyAccessToken 隐私访问令牌（可空）
     * @return 更新后的元数据
     */
    FileItemInfo move(String id, MoveRequest req, String userId, boolean isAdmin,
                      String privacyAccessToken);

    /**
     * 软删除文件或文件夹：先将元数据标记为 DELETED（带时间戳），再删除磁盘内容。
     * 如果磁盘删除失败，元数据已安全标记——后续可由清理任务进行数据修复。
     *
     * @param id                 文件项 ID
     * @param userId             调用者 ID
     * @param isAdmin            是否拥有 ROLE_ADMIN
     * @param privacyAccessToken 隐私访问令牌（可空）
     */
    void delete(String id, String userId, boolean isAdmin, String privacyAccessToken);

    /**
     * 将指定文件夹标记为隐私文件夹并设置访问密码。
     * <p>
     * 仅目录类型可设置为隐私模式。密码使用 BCrypt 哈希后存储。
     * 设置新密码后，该文件夹的所有已有访问会话将失效。
     *
     * @param id     文件夹 ID
     * @param req    包含新隐私密码的请求
     * @param userId 调用者 ID
     * @return 更新后的文件夹元数据
     * @throws com.baiflow.common.exception.BusinessException FILE_OPERATION_FAILED 目标不是文件夹类型
     */
    FileItemInfo setPrivacy(String id, SetPrivacyRequest req, String userId);

    /**
     * 取消文件夹的隐私保护，恢复为 NORMAL 模式。
     * <p>
     * 同时清除该文件夹的隐私密码哈希和所有访问会话。
     *
     * @param id     文件夹 ID
     * @param userId 调用者 ID
     * @return 更新后的文件夹元数据
     */
    FileItemInfo removePrivacy(String id, String userId);

    /**
     * 验证隐私文件夹密码，成功则生成短期访问会话并返回访问令牌。
     * <p>
     * 访问令牌有效期 30 分钟，用于在有效期内免重复输入隐私密码。
     * 访问令牌的哈希存储在 {@code private_folder_access} 表中。
     * 验证前会先清理过期的访问会话。
     *
     * @param id     文件夹 ID
     * @param req    包含隐私密码的请求
     * @param userId 调用者 ID
     * @return 包含 "accessToken" 和 "expiresAt" 的 Map
     * @throws com.baiflow.common.exception.BusinessException PRIVATE_PASSWORD_INVALID 密码错误
     */
    Map<String, Object> verifyPrivacy(String id, VerifyPrivacyRequest req, String userId);
}
