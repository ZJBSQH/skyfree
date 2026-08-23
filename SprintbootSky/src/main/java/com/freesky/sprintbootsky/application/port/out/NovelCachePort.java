package com.freesky.sprintbootsky.application.port.out;

import com.freesky.sprintbootsky.application.novel.NovelSummary;

/**
 * 小说热点缓存端口。Redis 只做热点缓存，MySQL 才是最终事实来源；
 * Application 不感知 Redis key、RedisTemplate 或序列化细节。
 */
public interface NovelCachePort {

    void cacheLatestNovel(Long userId, NovelSummary summary);

    void cacheNovelSummary(Long novelId, NovelSummary summary);

    void evictRecentProjects(Long userId);
}
