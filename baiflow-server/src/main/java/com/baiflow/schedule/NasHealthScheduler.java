package com.baiflow.schedule;

import com.baiflow.storage.service.BfStorageRootService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * <p>
 * <b>开关</b>：{@code baiflow.nas.health-check-enabled}（环境变量
 * {@code BAIFLOW_NAS_HEALTH_CHECK_ENABLED}），默认 **false** —— 没有 NAS 硬件时不必空转；
 * 接上 NAS 后改成 true 即可恢复。关闭期间存储根状态不再自动刷新，需要时用
 * {@code POST /api/storage-roots/{id}/check} 手工检测。
 * <p>
 * 库里没有 NAS_MOUNT 类型的存储根时本任务本就是空转（查不到、不记日志），关闭它是为了
 * 在没有 NAS 的阶段少一个无意义的后台探测。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "baiflow.nas.health-check-enabled", havingValue = "true", matchIfMissing = true)
public class NasHealthScheduler {

    @Autowired
    private BfStorageRootService storageService;

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
