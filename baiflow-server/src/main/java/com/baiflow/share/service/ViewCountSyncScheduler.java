package com.baiflow.share.service;

import com.baiflow.share.entity.ShareLink;
import com.baiflow.share.mapper.ShareLinkMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 定期将 Redis 中的分享访问计数同步到数据库。
 * <p>
 * 每 60 秒扫描一次 Redis 中 {@code share:view:*} 键，
 * 将其值累加到对应 share_link 的 view_count 字段，然后清除 Redis 键。
 */
@Component
public class ViewCountSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ViewCountSyncScheduler.class);
    private static final String REDIS_SHARE_VIEW_KEY = "share:view:";

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ShareLinkMapper shareMapper;

    @Scheduled(fixedRate = 60_000)
    public void syncViewCounts() {
        Set<String> keys = redisTemplate.keys(REDIS_SHARE_VIEW_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int synced = 0;
        for (String key : keys) {
            try {
                String countStr = redisTemplate.opsForValue().get(key);
                if (countStr == null) continue;

                int delta = Integer.parseInt(countStr);
                if (delta <= 0) {
                    redisTemplate.delete(key);
                    continue;
                }

                String shareLinkId = key.substring(REDIS_SHARE_VIEW_KEY.length());
                ShareLink sl = shareMapper.selectById(shareLinkId);
                if (sl != null) {
                    sl.setViewCount(sl.getViewCount() + delta);
                    shareMapper.updateById(sl);
                }

                // 扣减已同步的计数
                long remaining = redisTemplate.opsForValue().decrement(key, delta);
                if (remaining <= 0) {
                    redisTemplate.delete(key);
                }
                synced++;
            } catch (Exception e) {
                log.warn("同步分享访问计数失败: key={}, error={}", key, e.getMessage());
            }
        }

        if (synced > 0) {
            log.debug("已同步 {} 个分享链接的访问计数", synced);
        }
    }
}
