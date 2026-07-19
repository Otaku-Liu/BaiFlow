package com.baiflow.health.service.impl;

import com.baiflow.health.service.HealthService;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查服务实现 — 报告服务状态和数据库连通性。
 */
@Service
public class HealthServiceImpl implements HealthService {

    private final DataSource dataSource;

    public HealthServiceImpl(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> db = databaseStatus();
        payload.put("status", db.get("status"));
        payload.put("service", "baiflow-server");
        payload.put("timestamp", OffsetDateTime.now().toString());
        payload.put("components", Map.of("database", db));
        return payload;
    }

    /**
     * 检测数据库连接状态：尝试获取连接并在 2 秒内验证其有效性。
     */
    private Map<String, Object> databaseStatus() {
        try (Connection c = dataSource.getConnection()) {
            return Map.of("status", c.isValid(2) ? "UP" : "DOWN");
        } catch (Exception e) {
            return Map.of("status", "DOWN", "message", e.getClass().getSimpleName());
        }
    }
}
