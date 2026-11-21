package com.documind.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(15);
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(30);

    private static final String ATTEMPTS_PREFIX = "login:attempts:";
    private static final String LOCKOUT_PREFIX = "login:lockout:";

    public void recordFailedAttempt(String identifier) {
        String key = ATTEMPTS_PREFIX + identifier;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, WINDOW_DURATION);
        }
        log.debug("Failed login attempt for {}: count={}", identifier, attempts);
    }

    public void recordSuccess(String identifier) {
        redisTemplate.delete(ATTEMPTS_PREFIX + identifier);
        redisTemplate.delete(LOCKOUT_PREFIX + identifier);
    }

    public boolean isLockedOut(String identifier) {
        String lockoutKey = LOCKOUT_PREFIX + identifier;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey));
    }

    public int getRemainingAttempts(String identifier) {
        String key = ATTEMPTS_PREFIX + identifier;
        String value = redisTemplate.opsForValue().get(key);
        int attempts = value != null ? Integer.parseInt(value) : 0;
        return Math.max(0, MAX_ATTEMPTS_PER_WINDOW - attempts);
    }

    public void checkAndThrowIfLockedOut(String identifier) {
        if (isLockedOut(identifier)) {
            throw new IllegalStateException("Too many login attempts - account temporarily locked");
        }

        String key = ATTEMPTS_PREFIX + identifier;
        String value = redisTemplate.opsForValue().get(key);
        int attempts = value != null ? Integer.parseInt(value) : 0;

        // Rate limit: max 5 attempts per 15-minute window
        if (attempts >= MAX_ATTEMPTS_PER_WINDOW) {
            String lockoutKey = LOCKOUT_PREFIX + identifier;
            redisTemplate.opsForValue().set(lockoutKey, "locked", LOCKOUT_DURATION);
            log.warn("Account rate limited for {}", identifier);
            throw new IllegalStateException("Too many login attempts");
        }
    }
}