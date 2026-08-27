package com.dayan.food.service.impl;

import com.dayan.food.entity.vo.CaptchaVO;
import com.dayan.food.service.CaptchaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final String KEY_PREFIX = "dayan-food:captcha:";
    private static final String ATTEMPT_KEY_PREFIX = "dayan-food:captcha-attempt:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final Duration expiration;
    private final int maxAttempts;

    public CaptchaServiceImpl(
            StringRedisTemplate redisTemplate,
            @Value("${app.captcha.expiration:5m}") Duration expiration,
            @Value("${app.captcha.max-attempts:3}") int maxAttempts
    ) {
        this.redisTemplate = redisTemplate;
        this.expiration = expiration;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public CaptchaVO issue() {
        int left = 1 + SECURE_RANDOM.nextInt(20);
        int right = 1 + SECURE_RANDOM.nextInt(20);
        boolean add = SECURE_RANDOM.nextBoolean();
        int answer = add ? left + right : Math.abs(left - right);
        String question = add ? (left + " + " + right) : (Math.max(left, right) + " - " + Math.min(left, right));

        String captchaId = UUID.randomUUID().toString();
        // Redis 键不包含题目明文，值只保存答案摘要，避免缓存泄露时直接暴露答案。
        redisTemplate.opsForValue().set(KEY_PREFIX + captchaId, digest(captchaId, String.valueOf(answer)), expiration);
        return new CaptchaVO(captchaId, question + " = ?");
    }

    @Override
    public void verify(String captchaId, String answer) {
        String normalizedId = captchaId == null ? "" : captchaId.trim();
        String normalizedAnswer = answer == null ? "" : answer.trim();
        if (normalizedId.isEmpty() || normalizedAnswer.isEmpty()) {
            throw new IllegalArgumentException("请完成人机验证");
        }

        String answerKey = KEY_PREFIX + normalizedId;
        String expectedDigest = redisTemplate.opsForValue().get(answerKey);
        if (expectedDigest == null) {
            throw new IllegalArgumentException("验证码已失效，请获取新的验证题");
        }

        String attemptKey = ATTEMPT_KEY_PREFIX + normalizedId;
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptKey, expiration);
        }
        if (attempts != null && attempts > maxAttempts) {
            redisTemplate.delete(List.of(answerKey, attemptKey));
            throw new IllegalArgumentException("验证次数过多，请获取新的验证题");
        }

        byte[] expected = expectedDigest.getBytes(StandardCharsets.UTF_8);
        byte[] actual = digest(normalizedId, normalizedAnswer).getBytes(StandardCharsets.UTF_8);
        // 恒定时间比较，避免摘要比较过程中泄露有效答案的前缀信息。
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("验证答案不正确，请重新输入");
        }

        // 一次性消费：校验通过即删除答案键与计数键。
        redisTemplate.delete(List.of(answerKey, attemptKey));
    }

    private String digest(String captchaId, String answer) {
        return sha256(captchaId + ":" + answer);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}