package com.konkuk.propit.global.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.konkuk.propit.global.exception.BaseException;
import com.konkuk.propit.global.exception.code.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 로컬 Redis(localhost:6379)가 떠 있을 때만 실행된다. 없으면 건너뛴다.
 */
class RedisLockTest {

    private static final Duration TTL = Duration.ofSeconds(5);
    private static final Duration WAIT = Duration.ofSeconds(5);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private RedisLock redisLock;
    private ExecutorService executor;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);

        boolean available;
        try (var connection = connectionFactory.getConnection()) {
            available = "PONG".equals(connection.ping());
        } catch (Exception e) {
            available = false;
        }
        assumeTrue(available, "로컬 Redis가 없어서 건너뜀");
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        redisLock = new RedisLock(redisTemplate);
        executor = Executors.newFixedThreadPool(5);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void 같은_키는_한_번에_하나만_실행된다() throws Exception {
        String key = randomKey();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(executor.submit(() -> redisLock.executeWithLock(key, TTL, WAIT, () -> {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                sleep(100);
                running.decrementAndGet();
                return null;
            })));
        }
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }

        assertThat(maxRunning.get()).isEqualTo(1);
    }

    @Test
    void 다른_키는_서로_기다리지_않는다() throws Exception {
        long start = System.nanoTime();

        Future<?> first = executor.submit(() -> redisLock.executeWithLock(randomKey(), TTL, WAIT, () -> sleep(500)));
        Future<?> second = executor.submit(() -> redisLock.executeWithLock(randomKey(), TTL, WAIT, () -> sleep(500)));
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);

        // 한 줄로 처리됐다면 1초 이상 걸린다
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(900);
    }

    @Test
    void 대기_시간을_넘기면_LOCK_WAIT_TIMEOUT_예외가_난다() throws Exception {
        String key = randomKey();
        Future<?> holder = executor.submit(() -> redisLock.executeWithLock(key, TTL, WAIT, () -> sleep(1000)));
        sleep(100);

        assertThatThrownBy(() -> redisLock.executeWithLock(key, TTL, Duration.ofMillis(200), () -> null))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOCK_WAIT_TIMEOUT);

        holder.get(5, TimeUnit.SECONDS);
    }

    @Test
    void TTL이_지난_뒤_다른_요청이_잡은_락은_지우지_않는다() throws Exception {
        String key = randomKey();

        // A: TTL 300ms인데 600ms 동안 실행된다
        Future<?> a = executor.submit(() ->
                redisLock.executeWithLock(key, Duration.ofMillis(300), WAIT, () -> sleep(600)));
        sleep(400);

        // B: A의 락이 만료된 뒤에 잡는다. A가 끝나며 락을 해제해도 B의 락은 남아 있어야 한다.
        AtomicBoolean stillLockedAfterAReleased = new AtomicBoolean();
        redisLock.executeWithLock(key, TTL, WAIT, () -> {
            try {
                a.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            stillLockedAfterAReleased.set(Boolean.TRUE.equals(redisTemplate.hasKey("lock:" + key)));
            return null;
        });

        assertThat(stillLockedAfterAReleased).isTrue();
        assertThat(redisTemplate.hasKey("lock:" + key)).isFalse();
    }

    private static String randomKey() {
        return "test:" + UUID.randomUUID();
    }

    private static Object sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
