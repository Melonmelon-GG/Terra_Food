package com.dayan.food.service;

import com.dayan.food.mapper.AppUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class AbuseBudgetService {
    private static final DefaultRedisScript<Long> TAKE = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            if n > tonumber(ARGV[2]) then return math.max(1, redis.call('PTTL', KEYS[1])) end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final AppUserMapper users;
    private final int mailGlobalLimit;
    private final int captchaGlobalLimit;
    public AbuseBudgetService(StringRedisTemplate redis, AppUserMapper users,
            @Value("${app.abuse.mail-global-per-hour:100}") int mailGlobalLimit,
            @Value("${app.abuse.captcha-global-per-five-minutes:600}") int captchaGlobalLimit) {
        this.redis = redis; this.users = users;
        this.mailGlobalLimit = mailGlobalLimit; this.captchaGlobalLimit = captchaGlobalLimit;
    }
    public String login(String address, String identity) {
        require("login:ip:" + digest(address), 60, Duration.ofMinutes(5));
        var user = users.findByUsernameOrEmail(normalize(identity));
        String account = user == null ? "unknown:" + normalize(identity) : "subject:" + user.getSubjectId();
        String key = key("login:failure:" + digest(account));
        String failures = redis.opsForValue().get(key);
        if (failures != null && Long.parseLong(failures) >= 10) {
            Long ttl = redis.getExpire(key);
            reject(ttl == null || ttl < 1 ? 1 : ttl);
        }
        return key;
    }
    public void loginFailed(String key) {
        Long result = redis.execute(TAKE, List.of(key), Long.toString(Duration.ofMinutes(5).toMillis()), "10");
        if (result == null) unavailable();
    }
    public void loginSucceeded(String key) { redis.delete(key); }
    public void mail(String address, String target) {
        require("mail:ip:" + digest(address), 10, Duration.ofHours(1));
        require("mail:target:" + digest(normalize(target)), 5, Duration.ofHours(1));
        require("mail:global", mailGlobalLimit, Duration.ofHours(1));
    }
    public void captcha(String address, String sessionId) {
        require("captcha:ip:" + digest(address), 20, Duration.ofMinutes(5));
        require("captcha:session:" + digest(sessionId), 10, Duration.ofMinutes(5));
        require("captcha:global", captchaGlobalLimit, Duration.ofMinutes(5));
    }
    private void require(String suffix, long limit, Duration window) {
        Long wait = redis.execute(TAKE, List.of(key(suffix)), Long.toString(window.toMillis()), Long.toString(limit));
        if (wait == null) unavailable();
        if (wait > 0) reject(Math.max(1, (wait + 999) / 1000));
    }
    private static void reject(long seconds) {
        throw new RateLimitException(seconds);
    }
    private static void unavailable() {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请求预算服务暂不可用，请稍后重试");
    }
    public static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static String key(String suffix) { return "dayan-food:budget:" + suffix; }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)), 0, 12); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static final class RateLimitException extends ResponseStatusException {
        private final org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        public RateLimitException(long seconds) {
            super(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试");
            headers.set("Retry-After", Long.toString(seconds));
        }
        @Override public org.springframework.http.HttpHeaders getHeaders() { return headers; }
    }
}
