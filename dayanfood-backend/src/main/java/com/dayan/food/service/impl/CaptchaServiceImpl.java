package com.dayan.food.service.impl;

import com.dayan.food.entity.vo.CaptchaVO;
import com.dayan.food.service.CaptchaService;
import com.dayan.food.service.AtomicChallengeStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AtomicChallengeStore challenges;
    private final Duration expiration;
    private final int maxAttempts;

    public CaptchaServiceImpl(
            AtomicChallengeStore challenges,
            @Value("${app.captcha.expiration:5m}") Duration expiration,
            @Value("${app.captcha.max-attempts:3}") int maxAttempts
    ) {
        this.challenges = challenges;
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
        challenges.issue("captcha", captchaId, String.valueOf(answer), expiration);
        return new CaptchaVO(captchaId, question + " = ?");
    }

    @Override
    public void verify(String captchaId, String answer) {
        String normalizedId = captchaId == null ? "" : captchaId.trim();
        String normalizedAnswer = answer == null ? "" : answer.trim();
        if (normalizedId.isEmpty() || normalizedAnswer.isEmpty()) {
            throw new IllegalArgumentException("请完成人机验证");
        }

        challenges.claim("captcha", normalizedId, normalizedAnswer, maxAttempts, false);
    }

}
