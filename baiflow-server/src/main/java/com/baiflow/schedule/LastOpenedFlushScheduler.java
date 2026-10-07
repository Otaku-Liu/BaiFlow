package com.baiflow.schedule;

import com.baiflow.file.service.BfFileItemService;
import com.baiflow.file.service.LastOpenedBuffer;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 把「进入目录」攒下的 {@code last_opened_at} 批量落库。
 * <p>
 * 每 5 秒一条 {@code UPDATE bf_file_item SET last_opened_at = ? WHERE id IN (...)}：
 * 一次往返写完整批，读路径（{@code GET /api/files}）不再写库、不再拿行锁。
 * <p>
 * 代价：长摁弹窗里的「上次打开时间」最多滞后一个周期；进程被强杀时最多丢一个周期的记录 ——
 * 该字段只作参考，不参与任何判定。见 {@code docs/01-architecture.md}「last_opened_at 异步落库」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LastOpenedFlushScheduler {

    /** 刷库周期（毫秒）：也是「上次打开时间」的最大滞后 */
    private static final long FLUSH_INTERVAL_MS = 5_000;

    private final LastOpenedBuffer buffer;
    private final BfFileItemService fileService;

    @Scheduled(fixedRate = FLUSH_INTERVAL_MS)
    public void flush() {
        Set<String> ids = buffer.drain();
        if (ids.isEmpty()) {
            return;
        }
        try {
            fileService.touchLastOpenedBatch(ids);
        } catch (Exception e) {
            // 落库失败只丢这一批参考信息，不该影响定时任务后续周期（更不该打断任何请求）
            log.warn("上次打开时间批量落库失败：{} 条，{}", ids.size(), e.getMessage());
        }
    }

    /** 应用关闭前补刷一次，尽量不丢最后几秒的记录 */
    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }
}
