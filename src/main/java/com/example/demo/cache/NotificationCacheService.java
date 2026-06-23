package com.example.demo.cache;

import com.example.demo.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache-aside accelerator over Redis. Redis is an OPTIONAL dependency: every operation is wrapped so
 * an outage degrades to a cache-miss (reads) or no-op (writes) instead of throwing to the caller.
 */
@Service
public class NotificationCacheService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCacheService.class);
    private static final String KEY_BY_ID = "notification:";
    private static final String KEY_RECENT = "notifications:recent";

    private final RedisTemplate<String, Object> redis;
    private final int recentSize;
    private final long ttlSeconds;

    public NotificationCacheService(RedisTemplate<String, Object> redis,
                                    @Value("${notification.cache.recent-size:10}") int recentSize,
                                    @Value("${notification.cache.ttl-seconds:3600}") long ttlSeconds) {
        this.redis = redis;
        this.recentSize = recentSize;
        this.ttlSeconds = ttlSeconds;
    }

    // --- resilience wrappers: Redis is an accelerator, never a hard dependency ---
    private void safeRun(String op, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Redis {} failed; continuing without cache", op, e);
        }
    }

    private <T> T safeGet(String op, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            log.warn("Redis {} failed; treating as cache-miss", op, e);
            return fallback;
        }
    }

    public void putById(Notification n) {
        safeRun("putById", () -> redis.opsForValue().set(KEY_BY_ID + n.getId(), n, Duration.ofSeconds(ttlSeconds)));
    }

    public Optional<Notification> getById(Long id) {
        return safeGet("getById",
                () -> Optional.ofNullable((Notification) redis.opsForValue().get(KEY_BY_ID + id)),
                Optional.empty());
    }

    public void evict(Long id) {
        safeRun("evict", () -> redis.delete(KEY_BY_ID + id));
    }

    /**
     * Push newest to front, trim to recentSize — executed in a single MULTI/EXEC transaction so
     * LPUSH and LTRIM are atomic. Without this, concurrent writers could interleave between the two
     * commands and momentarily leave the list longer than recentSize (race condition).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void pushRecent(Notification n) {
        safeRun("pushRecent", () -> redis.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) {
                operations.multi();
                operations.opsForList().leftPush(KEY_RECENT, n);
                operations.opsForList().trim(KEY_RECENT, 0, recentSize - 1);
                return operations.exec();
            }
        }));
    }

    /** Invalidate the recent list so the next read rebuilds it from DB (used on update/delete). */
    public void evictRecent() {
        safeRun("evictRecent", () -> redis.delete(KEY_RECENT));
    }

    /** Replace the recent list wholesale (used to rebuild from DB) — atomic via MULTI/EXEC. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void replaceRecent(List<Notification> notifications) {
        safeRun("replaceRecent", () -> redis.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) {
                operations.multi();
                operations.delete(KEY_RECENT);
                for (int i = notifications.size() - 1; i >= 0; i--) {   // oldest first so newest ends at head
                    operations.opsForList().leftPush(KEY_RECENT, notifications.get(i));
                }
                operations.opsForList().trim(KEY_RECENT, 0, recentSize - 1);
                return operations.exec();
            }
        }));
    }

    @SuppressWarnings("unchecked")
    public List<Notification> getRecent() {
        return safeGet("getRecent",
                () -> {
                    List<Object> raw = redis.opsForList().range(KEY_RECENT, 0, recentSize - 1);
                    return raw == null ? List.<Notification>of() : (List<Notification>) (List<?>) raw;
                },
                List.of());
    }
}
