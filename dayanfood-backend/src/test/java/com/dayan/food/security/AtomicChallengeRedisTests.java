package com.dayan.food.security;

import com.dayan.food.mapper.ChallengeRedemptionMapper;
import com.dayan.food.service.AtomicChallengeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Run only against the disposable audit Redis; no FLUSHDB or shared-key scans. */
@EnabledIfEnvironmentVariable(named = "AUDIT_REDIS_PORT", matches = "[0-9]+")
class AtomicChallengeRedisTests {
    @Test void concurrentConsumeAndGenerationReplacementAreAtomic() throws Exception {
        var factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("AUDIT_REDIS_PORT")));
        factory.afterPropertiesSet(); factory.start();
        try {
            var redis = new StringRedisTemplate(factory);
            var store = new AtomicChallengeStore(redis, mock(ChallengeRedemptionMapper.class));
            String identity = UUID.randomUUID().toString();
            String first = store.issue("captcha", identity, "correct", Duration.ofSeconds(60));
            var start = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var operation = (java.util.concurrent.Callable<Boolean>) () -> {
                    start.await(5, TimeUnit.SECONDS);
                    try { store.claim("captcha", identity, "correct", 3, false); return true; }
                    catch (IllegalArgumentException rejected) { return false; }
                };
                var a=workers.submit(operation); var b=workers.submit(operation); start.countDown();
                assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
            }
            store.issue("captcha", identity, "new", Duration.ofSeconds(60));
            store.revoke("captcha", identity, first);
            assertDoesNotThrow(() -> store.claim("captcha", identity, "new", 3, false));

            store.issue("captcha", identity, "third", Duration.ofSeconds(60));
            for (int i=0;i<3;i++) assertThrows(IllegalArgumentException.class, () -> store.claim("captcha",identity,"wrong",3,false));
            store.issue("captcha", identity, "fourth", Duration.ofSeconds(60));
            assertDoesNotThrow(() -> store.claim("captcha", identity, "fourth",3,false));
        } finally { factory.destroy(); }
    }
}
