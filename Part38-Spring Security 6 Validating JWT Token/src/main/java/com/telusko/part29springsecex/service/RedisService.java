package com.telusko.part29springsecex.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;

    // Usado solo cuando app.token-blacklist.redis-enabled=false: token -> instante de expiracion (ms)
    private final Map<String, Long> localTokens = new ConcurrentHashMap<>();

    public RedisService(StringRedisTemplate redisTemplate,
                        @Value("${app.token-blacklist.redis-enabled:false}") boolean redisEnabled) {
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled;
    }

    // Store data with a custom TTL
    public void setTokenWithTTL(String key, String value, long ttl, TimeUnit timeUnit) {
        if (redisEnabled) {
            redisTemplate.opsForValue().set(key, value, ttl, timeUnit);
            return;
        }
        long now = System.currentTimeMillis();
        localTokens.values().removeIf(expiresAt -> expiresAt <= now);
        localTokens.put(key, now + timeUnit.toMillis(ttl));
    }

    // check if token exists in the database
    public boolean hasToken(String token) {
        if (redisEnabled) {
            return Boolean.TRUE.equals(redisTemplate.hasKey(token));
        }
        Long expiresAt = localTokens.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            localTokens.remove(token);
            return false;
        }
        return true;
    }
}
