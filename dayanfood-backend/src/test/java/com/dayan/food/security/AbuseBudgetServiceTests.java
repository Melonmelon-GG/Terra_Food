package com.dayan.food.security;

import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.AbuseBudgetService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AbuseBudgetServiceTests {
    @Test void UsernameEmailAndWhitespaceShareStableFailureBucket() {
        var redis=mock(StringRedisTemplate.class);var users=mock(AppUserMapper.class);
        ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.execute(any(RedisScript.class),anyList(),anyString(),anyString())).thenReturn(0L);
        var user=new AppUser("reader","encoded","Reader",UserRole.USER);
        ReflectionTestUtils.setField(user,"subjectId","stable-subject");
        when(users.findByUsernameOrEmail("reader")).thenReturn(user);
        when(users.findByUsernameOrEmail("reader@example.com")).thenReturn(user);
        var budget=new AbuseBudgetService(redis,users,100,600);
        assertEquals(budget.login("198.51.100.10"," Reader "),budget.login("198.51.100.10","READER@example.com"));
    }
    @Test void RetryAfterIsPreservedOnLimitException() {
        var failure=new AbuseBudgetService.RateLimitException(42);
        assertEquals(429,failure.getStatusCode().value());
        assertEquals("42",failure.getHeaders().getFirst("Retry-After"));
    }
}
