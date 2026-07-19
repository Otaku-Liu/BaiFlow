package com.baiflow.storage.scheduler;

import com.baiflow.storage.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * NAS 健康检查定时任务 — 定期检测 NAS_MOUNT 类型存储根目录的连通性。
 * <p>
 * 每隔 60 秒检查一次所有 NAS 挂载路径是否存在，自动更新 status：
 * <ul>
 *   <li>路径可访问 → ACTIVE</li>
 *   <li>路径不可访问 → OFFLINE</li>
 * </ul>
 * <p>
 * DISABLED 状态的存储根目录不会被检查（管理员手动禁用）。
 * 启用条件配置：{@code baiflow.nas.health-check-enabled=true}
 */
@Component
public class NasHealthScheduler {

    private static final Logger log = LoggerFactory.getLogger(NasHealthScheduler.class);

    private final StorageService storageService;

    public NasHealthScheduler(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 每 60 秒执行一次 NAS 健康检查。
     * 仅在 {@code baiflow.nas.health-check-enabled} 为 true 时执行（默认 true）。
     */
    @Scheduled(fixedRateString = "${baiflow.nas.health-check-interval-ms:60000}",
               initialDelayString = "${baiflow.nas.health-check-initial-delay-ms:10000}")
    public void checkNasHealth() {
        try {
            int updated = storageService.checkAllNasRoots();
            if (updated > 0) {
                log.info("NAS 健康检查完成，更新了 {} 个存储根目录的状态", updated);
            }
        } catch (Exception e) {
            log.error("NAS 健康检查执行失败", e);
        }
    }
}
