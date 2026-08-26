package com.dayan.food.service.impl;

import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.CaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationCodeServiceImplTests {

    private static final String EMAIL = "user@example.com";
    private static final String KEY_PREFIX = "dayan-food:registration:";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CaptchaService captchaService;

    private RegistrationCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RegistrationCodeServiceImpl(
                mailSender,
                redisTemplate,
                appUserMapper,
                captchaService,
                "noreply@example.com",
                Duration.ofMinutes(10),
                Duration.ofSeconds(60),
                5
        );
    }

    @Test
    void sendCodeNormalizesEmailAndStoresOnlyDigest() {
        doNothing().when(captchaService).verify(anyString(), anyString());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(true);

        service.sendCode("  User@Example.COM ", "captcha-1", "12");

        String identity = sha256(EMAIL);
        ArgumentCaptor<String> digestCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq(KEY_PREFIX + "code:" + identity),
                digestCaptor.capture(),
                eq(Duration.ofMinutes(10))
        );
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        String body = messageCaptor.getValue().getText();
        var matcher = Pattern.compile("\\d{6}").matcher(body == null ? "" : body);
        if (!matcher.find()) {
            throw new AssertionError("邮件正文中缺少六位验证码");
        }
        assertEquals(sha256(EMAIL + ":" + matcher.group()), digestCaptor.getValue());
        assertEquals(EMAIL, messageCaptor.getValue().getTo()[0]);
    }

    @Test
    void sendCodeRejectsRegisteredEmailBeforeSending() {
        doNothing().when(captchaService).verify(anyString(), anyString());
        when(appUserMapper.countByEmail(EMAIL)).thenReturn(1);

        assertThrows(IllegalArgumentException.class, () -> service.sendCode(EMAIL, "captcha-1", "12"));

        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void verifyNormalizesEmailAndConsumeDeletesTemporaryKeys() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String identity = sha256(EMAIL);
        String code = "123456";
        when(valueOperations.get(KEY_PREFIX + "code:" + identity)).thenReturn(sha256(EMAIL + ":" + code));
        when(valueOperations.increment(KEY_PREFIX + "attempt:" + identity)).thenReturn(1L);

        String normalizedEmail = service.verify("User@Example.COM", code);
        service.consume(normalizedEmail);

        assertEquals(EMAIL, normalizedEmail);
        verify(redisTemplate).expire(KEY_PREFIX + "attempt:" + identity, Duration.ofMinutes(10));
        verify(redisTemplate).delete(List.of(
                KEY_PREFIX + "code:" + identity,
                KEY_PREFIX + "attempt:" + identity
        ));
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
