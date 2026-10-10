package com.dayan.food.cache;

import org.springframework.cache.Cache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Eviction is best effort after commit; durable versioned keys provide public consistency. */
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

    private void safely(Runnable action){
        try{action.run();}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(CacheInvalidator.class).warn("Cache eviction failed after commit: {}",failure.getClass().getSimpleName());}
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safely(action);
                }
            });
        } else {
            safely(action);
        }
    }
}