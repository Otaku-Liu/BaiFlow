package com.baiflow.transfer.service;

import com.baiflow.transfer.dto.response.TransferTaskInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 传输任务服务接口 — 统一管理上传、下载、设备流转任务。
 */
public interface TransferService {

    /**
     * 分页查询当前用户的传输任务，支持按任务类型和状态筛选。
     *
     * @param userId   当前用户 ID
     * @param taskType 任务类型筛选（可选），传 null 表示不筛选
     * @param status   状态筛选（可选），传 null 表示不筛选
     * @param page     页码（从 1 开始）
     * @param size     每页数量
     * @return 分页传输任务列表
     */
    IPage<TransferTaskInfo> listTasks(String userId, String taskType, String status, int page, int size);

    /**
     * 查询单个传输任务的详情。
     *
     * @param taskId 任务 ID
     * @param userId 当前用户 ID（用于校验归属）
     * @return 任务详情
     * @throws com.baiflow.common.exception.BusinessException NOT_FOUND 任务不存在
     * @throws com.baiflow.common.exception.BusinessException FORBIDDEN 任务不属于当前用户
     */
    TransferTaskInfo getTask(String taskId, String userId);

    /**
     * 创建一个新的传输任务记录（通常由上传/下载流程自动调用）。
     *
     * @param userId   创建者 ID
     * @param taskType 任务类型（UPLOAD / DOWNLOAD / DEVICE_SEND）
     * @param source   来源描述
     * @param target   目标描述
     * @return 创建的任务信息
     */
    TransferTaskInfo createTask(String userId, String taskType, String source, String target);

    /**
     * 更新传输任务的进度百分比。
     *
     * @param taskId   任务 ID
     * @param progress 进度（0-100）
     */
    void updateProgress(String taskId, int progress);

    /**
     * 将传输任务标记为完成。
     */
    void markCompleted(String taskId);

    /**
     * 将传输任务标记为失败，并记录错误信息。
     */
    void markFailed(String taskId, String errorMessage);
}
