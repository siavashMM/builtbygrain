package com.builtbygrain.backend.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

public class ResilientCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ResilientCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache read failed for {} key {}; falling back to the database: {}",
            cache.getName(), key, exception.toString());
        log.debug("Cache read failure details", exception);
        try {
            cache.evict(key);
        } catch (RuntimeException evictionException) {
            log.debug("Could not evict the failed cache entry", evictionException);
        }
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache write failed for {} key {}; returning the database result: {}",
            cache.getName(), key, exception.toString());
        log.debug("Cache write failure details", exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache eviction failed for {} key {}; TTL expiry will remove it: {}",
            cache.getName(), key, exception.toString());
        log.debug("Cache eviction failure details", exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Cache clear failed for {}; TTL expiry will remove its entries: {}",
            cache.getName(), exception.toString());
        log.debug("Cache clear failure details", exception);
    }
}
