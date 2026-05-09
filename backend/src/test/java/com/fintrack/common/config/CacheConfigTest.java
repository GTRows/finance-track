package com.fintrack.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CacheConfig.class)
class CacheConfigTest {

    @Autowired CacheManager cacheManager;

    @Test
    void cacheManagerBeanIsWired() {
        assertThat(cacheManager).isNotNull();
    }

    @Test
    void exposesThreeNamedCaches() {
        assertThat(cacheManager.getCacheNames())
                .containsExactlyInAnyOrder(
                        CacheConfig.ASSETS_CACHE,
                        CacheConfig.USER_SETTINGS_CACHE,
                        CacheConfig.CATEGORY_LOOKUP_CACHE,
                        CacheConfig.ANALYTICS_PORTFOLIOS_COMPARE_CACHE);
    }

    @Test
    void allCachesAreCaffeineBacked() {
        assertThat(cacheManager.getCache(CacheConfig.ASSETS_CACHE))
                .isInstanceOf(CaffeineCache.class);
        assertThat(cacheManager.getCache(CacheConfig.USER_SETTINGS_CACHE))
                .isInstanceOf(CaffeineCache.class);
        assertThat(cacheManager.getCache(CacheConfig.CATEGORY_LOOKUP_CACHE))
                .isInstanceOf(CaffeineCache.class);
        assertThat(cacheManager.getCache(CacheConfig.ANALYTICS_PORTFOLIOS_COMPARE_CACHE))
                .isInstanceOf(CaffeineCache.class);
    }
}
