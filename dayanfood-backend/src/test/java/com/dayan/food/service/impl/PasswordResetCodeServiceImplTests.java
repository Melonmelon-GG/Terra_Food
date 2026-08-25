package com.dayan.food.service.impl;

import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetCodeServiceImplTests {

    private static final String USERNAME = "test_user";
    private static final String EMAIL = "user@example.com";
    private static final String KEY_PREFIX = "dayan-food:password-reset:";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PasswordResetCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetCodeServiceImpl(
                mailSender,
                redisTemplate,
                appUserMapper,
                "noreply@example.com",
                Duration.ofMinutes(10),
                Duration.ofSeconds(60),
                5
        );
    }

    @Test
    void sendCodeRequiresUsernameAndEmailToBelongToSameActiveUser() {
        when(appUserMapper.findByUsername(USERNAME)).thenReturn(activeUser("another@example.com"));

        assertThrows(IllegalArgumentException.class, () -> service.sendCode(USERNAME, EMAIL));

        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void sendCodeStoresDigestBoundToUsernameAndEmail() {
        when(appUserMapper.findByUsername(USERNAME)).thenReturn(activeUser(EMAIL));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(true);

        service.sendCode(USERNAME, " User@Example.COM ");

        String identity = sha256(USERNAME + ":" + EMAIL);
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
        assertEquals(sha256(USERNAME + ":" + EMAIL + ":" + matcher.group()), digestCaptor.getValue());
    }

    @Test
    void verifyAcceptsMatchingCodeAndConsumeDeletesTemporaryKeys() {
        when(appUserMapper.findByUsername(USERNAME)).thenReturn(activeUser(EMAIL));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String identity = sha256(USERNAME + ":" + EMAIL);
        String code = "123456";
        when(valueOperations.get(KEY_PREFIX + "code:" + identity))
                .thenReturn(sha256(USERNAME + ":" + EMAIL + ":" + code));
        when(valueOperations.increment(KEY_PREFIX + "attempt:" + identity)).thenReturn(1L);

        String normalizedEmail = service.verify(USERNAME, EMAIL, code);
        service.consume(USERNAME, normalizedEmail);

        assertEquals(EMAIL, normalizedEmail);
        verify(redisTemplate).expire(KEY_PREFIX + "attempt:" + identity, Duration.ofMinutes(10));
        verify(redisTemplate).delete(List.of(
                KEY_PREFIX + "code:" + identity,
                KEY_PREFIX + "attempt:" + identity
        ));
    }

    private AppUser activeUser(String email) {
        return new AppUser(USERNAME, "encoded-password", "测试用户", email, UserRole.USER);
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
