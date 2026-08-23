package com.freesky.sprintbootsky.infrastructure.redis;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tools.jackson.databind.ObjectMapper;
import com.freesky.sprintbootsky.application.novel.NovelSummary;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisNovelCacheAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisNovelCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisNovelCacheAdapter(redisTemplate, new ObjectMapper());
        // 部分用例不触发写缓存，使用 lenient 避免 Mockito strict stubbing 报错
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private NovelSummary summary() {
        return new NovelSummary(10L, 1L, "云上之城", "idea", "COMPLETED", 2, 1);
    }

    @Test
    void cacheLatestNovelWritesJsonWithTtl() {
        adapter.cacheLatestNovel(1L, summary());

        verify(valueOperations).set(
                eq("freesky:user:1:latest-novel"),
                contains("\"novelId\":10"),
                eq(Duration.ofMinutes(15)));
    }

    @Test
    void cacheNovelSummaryWritesJsonWithTtl() {
        adapter.cacheNovelSummary(10L, summary());

        verify(valueOperations).set(
                eq("freesky:novel:10:summary"),
                contains("\"status\":\"COMPLETED\""),
                eq(Duration.ofMinutes(15)));
    }

    @Test
    void evictRecentProjectsDeletesKey() {
        adapter.evictRecentProjects(1L);

        verify(redisTemplate).delete("freesky:user:1:recent-projects");
    }

    @Test
    void redisFailureIsSwallowedOnWrite() {
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> adapter.cacheLatestNovel(1L, summary())).doesNotThrowAnyException();
    }

    @Test
    void redisFailureIsSwallowedOnDelete() {
        when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> adapter.evictRecentProjects(1L)).doesNotThrowAnyException();
    }
}
