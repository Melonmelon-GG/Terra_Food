package com.dayan.food.cache;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.Cache;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 无活跃事务时 CacheInvalidator 应立即执行失效（单测环境没有事务管理器）。
 */
class CacheInvalidatorTests {

    private final CacheInvalidator invalidator = new CacheInvalidator();

    @Test
    void invalidateEvictsKeyImmediatelyWithoutTransaction() {
        Cache cache = Mockito.mock(Cache.class);

        invalidator.invalidate(cache, 42L);

        verify(cache).evict(42L);
    }

    @Test
    void clearClearsCacheImmediatelyWithoutTransaction() {
        Cache cache = Mockito.mock(Cache.class);

        invalidator.clear(cache);

        verify(cache).clear();
    }

    @Test
    void invalidateIgnoresMissingCache() {
        invalidator.invalidate(null, 42L);
        invalidator.clear(null);
        // 空缓存时应安全跳过，不应抛异常。
    }

    @Test
    void clearDoesNotEvictSingleKeys() {
        Cache cache = Mockito.mock(Cache.class);

        invalidator.clear(cache);

        verify(cache, never()).evict(Mockito.any());
    }
}