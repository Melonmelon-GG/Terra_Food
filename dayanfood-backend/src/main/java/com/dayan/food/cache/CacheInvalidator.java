package com.dayan.food.cache;

import org.springframework.cache.Cache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后再执行缓存失效的组件，避免“写库事务回滚但缓存已被清空 / 失效瞬间被并发
 * 读者用旧值回填”的时序错位（BUG-03 的根治路径）。
 *
 * <p>失效动作延迟到 afterCommit：事务回滚时不失效，事务提交后其他读线程再读取时
 * 必然拿到新值；无活跃事务时立即执行，保证非事务调用路径语义一致。</p>
 */
@Component
public class CacheInvalidator {

    public void invalidate(Cache cache, Object key) {
        if (cache == null) {
            return;
        }
        afterCommit(() -> cache.evict(key));
    }

    public void clear(Cache cache) {
        if (cache == null) {
            return;
        }
        afterCommit(cache::clear);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}