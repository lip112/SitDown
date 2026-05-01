package com.univsitdown.global.config;

import com.univsitdown.global.security.AuthStore;
import com.univsitdown.global.security.InMemoryAuthStore;
import com.univsitdown.global.security.RedisAuthStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Configuration
public class AuthStoreConfig {

    @Bean
    @Primary
    public AuthStore authStore(StringRedisTemplate redisTemplate) {
        try {
            redisTemplate.opsForValue().get("health-check");
            log.info("[AuthStore] Redis 연결 성공 — RedisAuthStore 사용");
            return new RedisAuthStore(redisTemplate);
        } catch (Exception e) {
            log.warn("[AuthStore] Redis 연결 실패 — InMemoryAuthStore 사용 (개발 환경 전용)");
            return new InMemoryAuthStore();
        }
    }
}
