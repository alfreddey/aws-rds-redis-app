package com.amalitech.todo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis cache wiring.
 *
 * <p>The {@link RedisCacheConfiguration} bean becomes the default for Spring Boot's
 * auto-configured {@code RedisCacheManager}: JSON value serialisation (human-readable
 * in Redis, no JDK serialisation) and a bounded TTL so cached views cannot go stale
 * indefinitely if an evict is ever missed.
 *
 * <p>The {@link CacheErrorHandler} makes the cache <em>non-critical</em>: if Redis is
 * unreachable, cache operations are logged and swallowed instead of failing the request,
 * so the app degrades gracefully to reading straight from RDS. This pairs with disabling
 * the Redis health indicator (see application.yml) so a cache outage never fails the ALB
 * health check.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheConfiguration cacheConfiguration(
            @Value("${app.cache.ttl-seconds:60}") long ttlSeconds) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache GET failed for {}::{} - falling back to source (RDS). Cause: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis cache PUT failed for {}::{} - continuing without caching. Cause: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache EVICT failed for {}::{}. Cause: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis cache CLEAR failed for {}. Cause: {}",
                        cache.getName(), exception.getMessage());
            }
        };
    }
}
