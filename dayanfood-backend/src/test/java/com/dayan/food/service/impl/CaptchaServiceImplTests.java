package com.dayan.food.service.impl;

import com.dayan.food.entity.vo.CaptchaVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptchaServiceImplTests {

    private static final String KEY_PREFIX = "dayan-food:captcha:";
    private static final String ATTEMPT_KEY_PREFIX = "dayan-food:captcha-attempt:";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CaptchaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CaptchaServiceImpl(redisTemplate, Duration.ofMinutes(5), 3);
    }

    @Test
    void issueStoresOnlyAnswerDigest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CaptchaVO captcha = service.issue();

        assertNotNull(captcha.captchaId());
        assertTrue(captcha.question().endsWith("= ?"));

        ArgumentCaptor<String> digestCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq(KEY_PREFIX + captcha.captchaId()),
                digestCaptor.capture(),
                eq(Duration.ofMinutes(5))
        );

        // 题目必须与存储的答案摘要一致：从提问提取算式并求解后比对。
        String question = captcha.question().replace(" = ?", "");
        int answer = solve(question);
        assertEquals(sha256(captcha.captchaId() + ":" + answer), digestCaptor.getValue());
        assertFalse(digestCaptor.getValue().contains(String.valueOf(answer)));
    }

    @Test
    void verifyConsumesChallengeOnSuccess() {
        String captchaId = "challenge-1";
        String answer = "17";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY_PREFIX + captchaId))
                .thenReturn(sha256(captchaId + ":" + answer));
        when(valueOperations.increment(ATTEMPT_KEY_PREFIX + captchaId)).thenReturn(1L);

        service.verify(captchaId, answer);

        verify(valueOperations).increment(ATTEMPT_KEY_PREFIX + captchaId);
        verify(redisTemplate).expire(ATTEMPT_KEY_PREFIX + captchaId, Duration.ofMinutes(5));
        verify(redisTemplate).delete(List.of(KEY_PREFIX + captchaId, ATTEMPT_KEY_PREFIX + captchaId));
    }

    @Test
    void verifyRejectsWrongAnswer() {
        String captchaId = "challenge-2";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY_PREFIX + captchaId))
                .thenReturn(sha256(captchaId + ":10"));
        when(valueOperations.increment(ATTEMPT_KEY_PREFIX + captchaId)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> service.verify(captchaId, "999"));

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void verifyRejectsExpiredChallenge() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY_PREFIX + "expired")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.verify("expired", "1"));
    }

    @Test
    void verifyRejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> service.verify("", "1"));
        assertThrows(IllegalArgumentException.class, () -> service.verify("id", " "));
    }

    @Test
    void verifyLocksChallengeAfterTooManyAttempts() {
        String captchaId = "challenge-3";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY_PREFIX + captchaId))
                .thenReturn(sha256(captchaId + ":5"));
        when(valueOperations.increment(ATTEMPT_KEY_PREFIX + captchaId)).thenReturn(4L);

        assertThrows(IllegalArgumentException.class, () -> service.verify(captchaId, "1"));

        verify(redisTemplate).delete(List.of(KEY_PREFIX + captchaId, ATTEMPT_KEY_PREFIX + captchaId));
    }

    private static int solve(String question) {
        String[] parts = question.trim().split("\\s+");
        int left = Integer.parseInt(parts[0]);
        int right = Integer.parseInt(parts[2]);
        return parts[1].equals("+") ? left + right : Math.max(left, right) - Math.min(left, right);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}