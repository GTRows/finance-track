package com.fintrack.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process Caffeine cache provider for Spring Cache. Three named caches with bespoke specs feed
 * the hot reads on the asset master, per-user settings, and per-user category lookup. Eviction is
 * driven explicitly by writer methods via {@code @CacheEvict} / {@code @CachePut}; in-process scope
 * means the writer's call stack handles invalidation deterministically without going through the
 * {@code ApplicationEventPublisher} boundary established in 25-01.
 */
@Configuration
public class CacheConfig {

    public static final String ASSETS_CACHE = "assets";
    public static final String USER_SETTINGS_CACHE = "userSettings";
    public static final String CATEGORY_LOOKUP_CACHE = "categoryLookup";
    public static final String ANALYTICS_PORTFOLIOS_COMPARE_CACHE = "analytics:portfolios:compare";
    public static final String ANALYTICS_CORRELATIONS_CACHE = "analytics:correlations";
    public static final String ANALYTICS_MONTE_CARLO_CACHE = "analytics:monteCarlo";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(
                List.of(
                        new CaffeineCache(
                                ASSETS_CACHE,
                                Caffeine.newBuilder()
                                        .expireAfterWrite(1, TimeUnit.HOURS)
                                        .maximumSize(200)
                                        .recordStats()
                                        .build()),
                        new CaffeineCache(
                                USER_SETTINGS_CACHE,
                                Caffeine.newBuilder()
                                        .expireAfterAccess(30, TimeUnit.MINUTES)
                                        .maximumSize(16)
                                        .recordStats()
                                        .build()),
                        new CaffeineCache(
                                CATEGORY_LOOKUP_CACHE,
                                Caffeine.newBuilder()
                                        .expireAfterAccess(30, TimeUnit.MINUTES)
                                        .maximumSize(16)
                                        .recordStats()
                                        .build()),
                        new CaffeineCache(
                                ANALYTICS_PORTFOLIOS_COMPARE_CACHE,
                                Caffeine.newBuilder()
                                        .expireAfterWrite(60, TimeUnit.SECONDS)
                                        .maximumSize(200)
                                        .recordStats()
                                        .build()),
                        new CaffeineCache(
                                ANALYTICS_CORRELATIONS_CACHE,
                                Caffeine.newBuilder()
                                        .expireAfterWrite(60, TimeUnit.SECONDS)
                                        .maximumSize(200)
                                        .recordStats()
                                        .build()),
                        new CaffeineCache(
                                ANALYTICS_MONTE_CARLO_CACHE,
                                Caffeine.newBuilder()
                                        .expireAfterWrite(60, TimeUnit.SECONDS)
                                        .maximumSize(200)
                                        .recordStats()
                                        .build())));
        return manager;
    }
}
