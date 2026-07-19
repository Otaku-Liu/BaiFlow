package com.baiflow.download.service;

import com.baiflow.download.dto.request.CreateDownloadRequest;
import com.baiflow.download.dto.response.DownloadTaskInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Map;

/**
 * 下载任务管理服务 — 通过 aria2 JSON-RPC 创建和管理下载任务。
 * <p>
 * 提供下载任务的创建、查询、暂停、恢复和删除能力。
 * 下载进度和状态的同步由后台定时任务驱动（{@code Aria2SyncScheduler}）。
 */
public interface DownloadService {

    /**
     * 创建下载任务：校验存储根目录和路径，通过 aria2 RPC 提交下载，
     * 将任务元数据持久化到 {@code download_task} 表。
     *
     * @param req    下载源 URL、目标存储根目录和相对路径
     * @param userId 创建者 ID
     * @return 已创建的下载任务信息
     * @throws com.baiflow.common.exception.BusinessException STORAGE_ROOT_OFFLINE 目标存储不可用
     * @throws com.baiflow.common.exception.BusinessException DOWNLOAD_ENGINE_ERROR  aria2 创建任务失败
     */
    DownloadTaskInfo createDownload(CreateDownloadRequest req, String userId);

    /**
     * 分页查询当前用户的下载任务列表，可选按状态筛选，按创建时间倒序排列。
     * 管理员可查看所有任务。
     *
     * @param userId  当前用户 ID
     * @param isAdmin 是否管理员
     * @param status  状态筛选（可选，为空则查全部）
     * @param page    页码（从 1 开始）
     * @param size    每页数量
     * @return 分页的下载任务信息列表
     */
    IPage<DownloadTaskInfo> listDownloads(String userId, boolean isAdmin, String status, int page, int size);

    /**
     * 根据任务 ID 获取下载任务详情。
     * 非管理员只能查询自己的任务。
     *
     * @param taskId  任务 ID
     * @param userId  当前用户 ID
     * @param isAdmin 是否管理员
     * @return 下载任务信息
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND 任务不存在
     * @throws com.baiflow.common.exception.BusinessException FORBIDDEN 无权查看
     */
    DownloadTaskInfo getById(String taskId, String userId, boolean isAdmin);

    /**
     * 暂停下载任务：通过 aria2 RPC 暂停，更新本地状态为 PAUSED。
     *
     * @param taskId  任务 ID
     * @param userId  当前用户 ID
     * @param isAdmin 是否管理员
     * @return 更新后的任务信息
     */
    DownloadTaskInfo pauseDownload(String taskId, String userId, boolean isAdmin);

    /**
     * 恢复下载任务：通过 aria2 RPC 恢复，更新本地状态为 RUNNING。
     *
     * @param taskId  任务 ID
     * @param userId  当前用户 ID
     * @param isAdmin 是否管理员
     * @return 更新后的任务信息
     */
    DownloadTaskInfo resumeDownload(String taskId, String userId, boolean isAdmin);

    /**
     * 删除下载任务：通过 aria2 RPC 删除，本地标记为 DELETED。
     * 如果任务已完成，仅标记本地记录，不调用 aria2。
     *
     * @param taskId  任务 ID
     * @param userId  当前用户 ID
     * @param isAdmin 是否管理员
     */
    void deleteDownload(String taskId, String userId, boolean isAdmin);

    /**
     * 定时同步所有活跃任务（WAITING / RUNNING / PAUSED）的状态。
     * 批量查询 aria2，更新本地进度、速度和状态。
     * 下载完成的任务自动创建 {@code file_item} 记录。
     *
     * @return 本次同步更新的任务数
     */
    int syncActiveTasks();
}
