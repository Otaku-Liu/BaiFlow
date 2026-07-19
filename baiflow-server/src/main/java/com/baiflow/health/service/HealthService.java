package com.baiflow.health.service;

import java.util.Map;

/**
 * 健康检查服务 — 上报服务和数据库连接状态。
 */
public interface HealthService {

    /**
     * 构建健康检查负载，包含整体状态、服务名称、ISO-8601 时间戳和各组件（目前仅数据库）状态。
     *
     * @return 健康信息 Map，包含 status、service、timestamp、components 键
     */
    Map<String, Object> health();
}
