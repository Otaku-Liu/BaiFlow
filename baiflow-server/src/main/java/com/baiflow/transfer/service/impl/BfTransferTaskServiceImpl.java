package com.baiflow.transfer.service.impl;

import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.transfer.dto.response.TransferTaskInfo;
import com.baiflow.transfer.entity.BfTransferTask;
import com.baiflow.transfer.enums.TransferTaskStatus;
import com.baiflow.transfer.enums.TransferTaskType;
import com.baiflow.transfer.mapper.BfTransferTaskMapper;
import com.baiflow.transfer.service.BfTransferTaskService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 传输任务服务实现。
 */
@Service
public class BfTransferTaskServiceImpl extends ServiceImpl<BfTransferTaskMapper, BfTransferTask> implements BfTransferTaskService {

    @Override
    public IPage<TransferTaskInfo> listTasks(String userId, String taskType, String status, int page, int size) {
        List<BfTransferTask> all = lambdaQuery()
                .eq(BfTransferTask::getCreatedBy, userId)
                .eq(taskType != null && !taskType.isBlank(), BfTransferTask::getTaskType, taskType)
                .eq(status != null && !status.isBlank(), BfTransferTask::getStatus, status)
                .orderByDesc(BfTransferTask::getCreatedAt)
                .list();
        int total = all.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<TransferTaskInfo> recs = (from < total ? all.subList(from, to) : List.<BfTransferTask>of())
                .stream().map(TransferTaskInfo::from).toList();
        IPage<TransferTaskInfo> r = new Page<>(page, size, total);
        r.setRecords(recs);
        return r;
    }

    @Override
    public TransferTaskInfo getTask(String taskId, String userId) {
        BfTransferTask t = getById(taskId);
        if (t == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "传输任务不存在"); }
        // 校验任务归属：用户只能查看自己的任务
        if (!t.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此任务");
        }
        return TransferTaskInfo.from(t);
    }

    @Override
    public TransferTaskInfo createTask(String userId, String taskType, String source, String target) {
        BfTransferTask t = new BfTransferTask();
        t.setCreatedBy(userId);
        t.setTaskType(TransferTaskType.valueOf(taskType));
        t.setSourceType(source);
        t.setTargetType(target);
        t.setStatus(TransferTaskStatus.WAITING);
        t.setProgress(0);
        save(t);
        return TransferTaskInfo.from(t);
    }

    @Override
    public void updateProgress(String taskId, int progress) {
        BfTransferTask t = getById(taskId);
        if (t == null) { return; }
        t.setProgress(Math.max(0, Math.min(100, progress)));
        if (progress > 0 && t.getStatus() == TransferTaskStatus.WAITING) {
            t.setStatus(TransferTaskStatus.RUNNING);
        }
        updateById(t);
    }

    @Override
    public void markCompleted(String taskId) {
        BfTransferTask t = getById(taskId);
        if (t == null) { return; }
        t.setStatus(TransferTaskStatus.COMPLETED);
        t.setProgress(100);
        updateById(t);
    }

    @Override
    public void markFailed(String taskId, String errorMessage) {
        BfTransferTask t = getById(taskId);
        if (t == null) { return; }
        t.setStatus(TransferTaskStatus.FAILED);
        t.setErrorMessage(errorMessage);
        updateById(t);
    }
}
