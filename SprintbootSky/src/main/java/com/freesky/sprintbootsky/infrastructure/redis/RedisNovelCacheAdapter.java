package com.freesky.sprintbootsky.infrastructure.redis;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.freesky.sprintbootsky.application.novel.NovelSummary;
import com.freesky.sprintbootsky.application.port.out.NovelCachePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NovelCachePort 的 Redis 实现。
 * Redis 只做热点缓存，MySQL 才是最终事实来源：
 * 任何 Redis 异常都只记录日志、不向上抛，绝不影响 MySQL 持久化与接口返回；
 * 缓存内容使用专用摘要 DTO（NovelSummary），绝不缓存持久化实体或完整聚合。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisNovelCacheAdapter implements NovelCachePort {

    private static final String LATEST_NOVEL_KEY = "freesky:user:%d:latest-novel";
    private static final String NOVEL_SUMMARY_KEY = "freesky:novel:%d:summary";
    private static final String RECENT_PROJECTS_KEY = "freesky:user:%d:recent-projects";

    /** 热点缓存 TTL：10-30 分钟区间，取 15 分钟。 */
    private static final Duration SUMMARY_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void cacheLatestNovel(Long userId, NovelSummary summary) {
        set(LATEST_NOVEL_KEY.formatted(userId), summary);
    }

    @Override
    public void cacheNovelSummary(Long novelId, NovelSummary summary) {
        set(NOVEL_SUMMARY_KEY.formatted(novelId), summary);
    }

    @Override
    public void evictRecentProjects(Long userId) {
        try {
            redisTemplate.delete(RECENT_PROJECTS_KEY.formatted(userId));
        } catch (RuntimeException exception) {
            log.warn("Redis 删除 recent-projects 缓存失败，忽略并继续: userId={}", userId, exception);
        }
    }

    private void set(String key, NovelSummary summary) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(summary), SUMMARY_TTL);
        } catch (RuntimeException exception) {
            // JacksonException 继承 RuntimeException，统一吞掉缓存层异常
            log.warn("Redis 写入缓存失败，忽略并继续: key={}", key, exception);
        }
    }
}
