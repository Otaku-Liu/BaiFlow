package com.baiflow.download.service;

import java.util.Map;

/**
 * aria2 JSON-RPC 客户端 — 封装与 aria2 下载引擎的通信。
 * <p>
 * 使用 JSON-RPC 2.0 协议与 aria2 守护进程通信。
 * aria2 RPC 地址通过 {@code baiflow.aria2.url} 配置，
 * 密钥通过 {@code baiflow.aria2.secret} 配置。
 */
public interface Aria2Client {

    /**
     * 提交一个新的下载任务（aria2.addUri）。
     *
     * @param url      下载源 URL
     * @param dirPath  下载目标目录的服务器绝对路径
     * @param fileName 下载文件名
     * @return aria2 返回的任务 GID
     * @throws com.baiflow.common.exception.BusinessException DOWNLOAD_ENGINE_ERROR RPC 调用失败
     */
    String addUri(String url, String dirPath, String fileName);

    /**
     * 查询下载任务状态（aria2.tellStatus）。
     *
     * @param gid aria2 任务 GID
     * @return 任务状态 Map，包含 status, totalLength, completedLength,
     *         downloadSpeed, errorMessage, files 等字段
     */
    Map<String, Object> tellStatus(String gid);

    /**
     * 暂停下载任务（aria2.pause）。
     *
     * @param gid aria2 任务 GID
     */
    void pause(String gid);

    /**
     * 恢复下载任务（aria2.unpause）。
     *
     * @param gid aria2 任务 GID
     */
    void unpause(String gid);

    /**
     * 删除下载任务（aria2.remove）。
     *
     * @param gid aria2 任务 GID
     */
    void remove(String gid);

    /**
     * 检查 aria2 服务是否可用。
     *
     * @return true 表示 aria2 在线
     */
    boolean isAvailable();
}
