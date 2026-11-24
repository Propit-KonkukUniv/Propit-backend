package com.konkuk.propit.global.lock;

import com.konkuk.propit.global.exception.BaseException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import static com.konkuk.propit.global.exception.code.ErrorCode.LOCK_WAIT_TIMEOUT;

/**
 * 키 단위 분산 락.
 * 같은 키끼리만 순서대로 실행되고, 다른 키는 서로 기다리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    private static final String KEY_PREFIX = "lock:";
    private static final long RETRY_INTERVAL_MILLIS = 200;

    // 내가 건 락일 때만 지운다. TTL이 지나 다른 요청이 새로 잡은 락을 지우지 않기 위함
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    /**
     * @param ttl         락이 자동으로 풀리는 시간. task 최대 실행 시간보다 길어야 한다.
     * @param waitTimeout 락을 얻기 위해 기다리는 최대 시간. 넘으면 LOCK_WAIT_TIMEOUT 예외.
     */
    public <T> T executeWithLock(String key, Duration ttl, Duration waitTimeout, Supplier<T> task) {
        String lockKey = KEY_PREFIX + key;
        String token = acquire(lockKey, ttl, waitTimeout);
        try {
            return task.get();
        } finally {
            release(lockKey, token);
        }
    }

    private String acquire(String lockKey, Duration ttl, Duration waitTimeout) {
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + waitTimeout.toNanos();

        while (true) {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                return token;
            }
            if (System.nanoTime() >= deadline) {
                throw new BaseException(LOCK_WAIT_TIMEOUT);
            }
            try {
                Thread.sleep(RETRY_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BaseException(LOCK_WAIT_TIMEOUT);
            }
        }
    }

    private void release(String lockKey, String token) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), token);
        } catch (Exception e) {
            // 해제에 실패해도 TTL이 지나면 풀린다. 작업 결과까지 실패로 만들지 않는다.
            log.warn("락 해제 실패: {}", lockKey, e);
        }
    }
}
