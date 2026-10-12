package com.dayan.food.service;

import com.dayan.food.mapper.ChallengeRedemptionMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Generation-scoped, single-use challenges. A failed business transaction requires a new code. */
@Service
public class AtomicChallengeStore {
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], 'generation', ARGV[1], 'digest', ARGV[2], 'attempts', 0, 'used', 0)
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<String> CLAIM = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 'EXPIRED' end
            if redis.call('HGET', KEYS[1], 'used') == '1' then return 'USED' end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts > tonumber(ARGV[2]) then return 'LIMIT' end
            if redis.call('HGET', KEYS[1], 'digest') ~= ARGV[1] then return 'INVALID' end
            redis.call('HSET', KEYS[1], 'used', 1)
            return redis.call('HGET', KEYS[1], 'generation')
            """, String.class);
    private static final DefaultRedisScript<Long> REMOVE = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'generation') == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_COOLDOWN = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ChallengeRedemptionMapper redemptions;

    public AtomicChallengeStore(StringRedisTemplate redis, ChallengeRedemptionMapper redemptions) {
        this.redis = redis;
        this.redemptions = redemptions;
    }

    public String issue(String purpose, String identity, String answer, Duration ttl) {
        String generation = UUID.randomUUID().toString();
        Long stored = redis.execute(ISSUE, List.of(key(purpose, identity)), generation, digest(identity + ":" + answer), Long.toString(ttl.toMillis()));
        if (!Long.valueOf(1).equals(stored)) throw new IllegalStateException("验证码服务暂不可用");
        return generation;
    }

    public void revoke(String purpose, String identity, String generation) {
        redis.execute(REMOVE, List.of(key(purpose, identity)), generation);
    }

    public void releaseCooldown(String cooldownKey, String lease) {
        redis.execute(RELEASE_COOLDOWN, List.of(cooldownKey), lease);
    }

    public void claim(String purpose, String identity, String answer, int maxAttempts, boolean businessRedemption) {
        if (businessRedemption && (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())) {
            throw new IllegalStateException("Code redemption requires the business transaction");
        }
        String result = redis.execute(CLAIM, List.of(key(purpose, identity)), digest(identity + ":" + answer), Integer.toString(maxAttempts));
        if (result == null) throw new IllegalStateException("验证码服务暂不可用");
        switch (result) {
            case "EXPIRED", "USED" -> throw new IllegalArgumentException("验证码已使用或失效，请重新获取");
            case "LIMIT" -> throw new IllegalArgumentException("验证码尝试次数过多，请重新获取");
            case "INVALID" -> throw new IllegalArgumentException("验证码不正确");
            default -> {
                // The unique generation prevents replay even if Redis is restored from an old snapshot.
                if (businessRedemption) redemptions.insert(result, purpose, digest(identity));
            }
        }
    }

    private static String key(String purpose, String identity) {
        return "dayan-food:challenge:" + purpose + ":" + digest(identity);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
