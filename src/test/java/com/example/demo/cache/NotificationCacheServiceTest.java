package com.example.demo.cache;

import com.example.demo.domain.Notification;
import com.example.demo.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies the graceful-degradation contract: when Redis is unavailable, reads behave as a cache-miss
 * and writes become no-ops — never an exception to the caller.
 */
@ExtendWith(MockitoExtension.class)
class NotificationCacheServiceTest {

    @Mock
    RedisTemplate<String, Object> redis;

    NotificationCacheService cache;

    @BeforeEach
    void setup() {
        cache = new NotificationCacheService(redis, 10, 3600);
    }

    private Notification sample() {
        Notification n = new Notification();
        n.setId(1L);
        n.setType(NotificationType.EMAIL);
        n.setRecipient("u@e.com");
        return n;
    }

    @Test
    void getById_whenRedisDown_returnsEmpty() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        assertTrue(cache.getById(1L).isEmpty());
    }

    @Test
    void getRecent_whenRedisDown_returnsEmptyList() {
        when(redis.opsForList()).thenThrow(new RuntimeException("redis down"));
        assertTrue(cache.getRecent().isEmpty());
    }

    @Test
    void putById_whenRedisDown_doesNotThrow() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        assertDoesNotThrow(() -> cache.putById(sample()));
    }

    @Test
    void pushRecent_whenRedisDown_doesNotThrow() {
        when(redis.execute(any(SessionCallback.class))).thenThrow(new RuntimeException("redis down"));
        assertDoesNotThrow(() -> cache.pushRecent(sample()));
    }

    @Test
    void evict_whenRedisDown_doesNotThrow() {
        when(redis.delete(any(String.class))).thenThrow(new RuntimeException("redis down"));
        assertDoesNotThrow(() -> cache.evict(1L));
        assertDoesNotThrow(() -> cache.evictRecent());
    }
}
