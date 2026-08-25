package com.dayan.food.service.impl;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
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
import java.util.List;
import java.util.Locale;

@Service
public class PasswordResetCodeServiceImpl implements PasswordResetCodeService {

    private static final String KEY_PREFIX = "dayan-food:password-reset:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final AppUserMapper appUserMapper;
    private final String from;
    private final Duration expiration;
    private final Duration resendInterval;
    private final int maxAttempts;

    public PasswordResetCodeServiceImpl(
            JavaMailSender mailSender,
            StringRedisTemplate redisTemplate,
            AppUserMapper appUserMapper,
            @Value("${app.registration-code.from:}") String from,
            @Value("${app.registration-code.expiration:10m}") Duration expiration,
            @Value("${app.registration-code.resend-interval:60s}") Duration resendInterval,
            @Value("${app.registration-code.max-attempts:5}") int maxAttempts
    ) {
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
        this.appUserMapper = appUserMapper;
        this.from = from;
        this.expiration = expiration;
        this.resendInterval = resendInterval;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void sendCode(String username, String email) {
        String normalizedUsername = username.trim();
        String normalizedEmail = normalizeEmail(email);
        requireMatchingUser(normalizedUsername, normalizedEmail);

        String identity = keyIdentity(normalizedUsername, normalizedEmail);
        String cooldownKey = KEY_PREFIX + "cooldown:" + identity;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1", resendInterval);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试");
        }

        String code = "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
        String codeKey = KEY_PREFIX + "code:" + identity;
        // Redis 中只保存账号标识与验证码的摘要，避免缓存泄露明文敏感数据。
        redisTemplate.opsForValue().set(
                codeKey,
                digest(normalizedUsername, normalizedEmail, code),
                expiration
        );

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
            redisTemplate.delete(List.of(codeKey, cooldownKey));
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
        requireMatchingUser(normalizedUsername, normalizedEmail);

        String identity = keyIdentity(normalizedUsername, normalizedEmail);
        String codeKey = KEY_PREFIX + "code:" + identity;
        String attemptKey = KEY_PREFIX + "attempt:" + identity;
        String expectedDigest = redisTemplate.opsForValue().get(codeKey);
        if (expectedDigest == null) {
            throw new IllegalArgumentException("验证码已失效，请重新获取");
        }

        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptKey, expiration);
        }
        if (attempts != null && attempts > maxAttempts) {
            redisTemplate.delete(List.of(codeKey, attemptKey));
            throw new IllegalArgumentException("验证码尝试次数过多，请重新获取");
        }

        byte[] expected = expectedDigest.getBytes(StandardCharsets.UTF_8);
        byte[] actual = digest(normalizedUsername, normalizedEmail, code).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("邮箱验证码不正确");
        }
        return normalizedEmail;
    }

    @Override
    public void consume(String username, String normalizedEmail) {
        String identity = keyIdentity(username.trim(), normalizedEmail);
        redisTemplate.delete(List.of(
                KEY_PREFIX + "code:" + identity,
                KEY_PREFIX + "attempt:" + identity
        ));
    }

    private void requireMatchingUser(String username, String normalizedEmail) {
        AppUser user = appUserMapper.findByUsername(username);
        if (user == null || !user.isActive() || user.getEmail() == null
                || !normalizedEmail.equals(normalizeEmail(user.getEmail()))) {
            throw new IllegalArgumentException("用户名与邮箱不匹配，或账号不可用");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String keyIdentity(String username, String email) {
        return sha256(username + ":" + email);
    }

    private String digest(String username, String email, String code) {
        return sha256(username + ":" + email + ":" + code);
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
