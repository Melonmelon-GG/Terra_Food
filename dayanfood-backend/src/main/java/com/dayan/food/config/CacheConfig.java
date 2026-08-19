package com.dayan.food.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 故障时回源数据库，避免缓存基础设施中断公开查询。
 */
@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log("读取", cache, key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log("写入", cache, key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log("清理", cache, key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log("清空", cache, "*", exception);
            }

            private void log(String operation, Cache cache, Object key, RuntimeException exception) {
                LOGGER.warn("Redis 缓存{}失败，已回退数据库：cache={}, key={}",
                        operation,
                        cache.getName(),
                        key,
                        exception);
            }
        };
    }
}
