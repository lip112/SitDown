package com.univsitdown.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration SPACE_TTL = Duration.ofSeconds(30);
    private static final Duration SEAT_TTL  = Duration.ofSeconds(10);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        try {
            connectionFactory.getConnection().ping();
            log.info("[Cache] Redis 연결 성공 — RedisCacheManager 사용");

            RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                    .serializeValuesWith(
                            RedisSerializationContext.SerializationPair.fromSerializer(
                                    new GenericJackson2JsonRedisSerializer()));

            return RedisCacheManager.builder(connectionFactory)
                    .withCacheConfiguration("space:list",   base.entryTtl(SPACE_TTL))
                    .withCacheConfiguration("space:detail", base.entryTtl(SPACE_TTL))
                    .withCacheConfiguration("seat:layout",  base.entryTtl(SEAT_TTL))
                    .build();
        } catch (Exception e) {
            log.warn("[Cache] Redis 연결 실패 — ConcurrentMapCacheManager 사용 (개발 환경 전용)");
            return new ConcurrentMapCacheManager("space:list", "space:detail", "seat:layout");
        }
    }
}
