package com.dayan.food.service.impl;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.AtomicChallengeStore;
import com.dayan.food.service.PasswordResetCodeService;
import com.dayan.food.service.RegistrationCodeDeliveryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;

@Service
public class PasswordResetCodeServiceImpl implements PasswordResetCodeService {

    private static final String KEY_PREFIX = "dayan-food:password-reset:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final AppUserMapper appUserMapper;
    private final AtomicChallengeStore challenges;
    private final String from;
    private final Duration expiration;
    private final Duration resendInterval;
    private final int maxAttempts;

    public PasswordResetCodeServiceImpl(
            JavaMailSender mailSender,
            StringRedisTemplate redisTemplate,
            AppUserMapper appUserMapper,
            AtomicChallengeStore challenges,
            @Value("${app.registration-code.from:}") String from,
            @Value("${app.registration-code.expiration:10m}") Duration expiration,
            @Value("${app.registration-code.resend-interval:60s}") Duration resendInterval,
            @Value("${app.registration-code.max-attempts:5}") int maxAttempts
    ) {
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
        this.appUserMapper = appUserMapper;
        this.challenges = challenges;
        this.from = from;
        this.expiration = expiration;
        this.resendInterval = resendInterval;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void sendCode(String username, String email) {
        String normalizedUsername = username.trim();
        String normalizedEmail = normalizeEmail(email);
        AppUser account = requireMatchingUser(normalizedUsername, normalizedEmail);

        String identity = keyIdentity(account.getSubjectId(), normalizedEmail);
        String cooldownKey = KEY_PREFIX + "cooldown:" + identity;
        String cooldownLease = java.util.UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(cooldownKey, cooldownLease, resendInterval);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试");
        }

        String code = "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
        // Redis 中只保存账号标识与验证码的摘要，避免缓存泄露明文敏感数据。
        String generation = challenges.issue("password-reset", account.getSubjectId() + ":" + normalizedEmail, code, expiration);

        try {
            if (from.isBlank()) {
                throw new RegistrationCodeDeliveryException("邮件发件人尚未配置");
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(normalizedEmail);
            message.setSubject("大炎珍馐志密码重置验证码");
            message.setText("您的密码重置验证码是：" + code + "\n\n验证码将在 "
                    + expiration.toMinutes() + " 分钟后失效。若非本人操作，请忽略此邮件。");
            mailSender.send(message);
        } catch (MailException | RegistrationCodeDeliveryException exception) {
            challenges.revoke("password-reset", account.getSubjectId() + ":" + normalizedEmail, generation);
            challenges.releaseCooldown(cooldownKey, cooldownLease);
            if (exception instanceof RegistrationCodeDeliveryException deliveryException) {
                throw deliveryException;
            }
            throw new RegistrationCodeDeliveryException("验证码邮件发送失败", exception);
        }
    }

    @Override
    public String verify(String username, String email, String code) {
        String normalizedUsername = username.trim();
        String normalizedEmail = normalizeEmail(email);
        AppUser account = requireMatchingUser(normalizedUsername, normalizedEmail);

        challenges.claim("password-reset", account.getSubjectId() + ":" + normalizedEmail, code, maxAttempts, true);
        return normalizedEmail;
    }

    @Override
    public void consume(String username, String normalizedEmail) {
        // Already atomically claimed by verify. Never delete a newer generation here.
    }

    private AppUser requireMatchingUser(String username, String normalizedEmail) {
        AppUser user = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                ? appUserMapper.findByUsernameForUpdate(username) : appUserMapper.findByUsername(username);
        if (user == null || !user.isActive() || user.getEmail() == null
                || !normalizedEmail.equals(normalizeEmail(user.getEmail()))) {
            throw new IllegalArgumentException("用户名与邮箱不匹配，或账号不可用");
        }
        return user;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String keyIdentity(String username, String email) {
        return sha256(username + ":" + email);
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
