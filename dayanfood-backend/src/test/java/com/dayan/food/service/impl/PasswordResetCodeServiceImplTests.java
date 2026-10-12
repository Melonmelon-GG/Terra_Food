package com.dayan.food.service.impl;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PasswordResetCodeServiceImplTests {
 @Test void resetChallengeBindsStableSubjectNotReusableUsername() {
  var users=mock(AppUserMapper.class);var store=mock(AtomicChallengeStore.class);
  var user=new AppUser("reader","encoded","Reader","reader@example.com",UserRole.USER);
  ReflectionTestUtils.setField(user,"subjectId","immutable-subject");when(users.findByUsername("reader")).thenReturn(user);
  var service=new PasswordResetCodeServiceImpl(mock(JavaMailSender.class),mock(StringRedisTemplate.class),users,store,"test@example.com",Duration.ofMinutes(10),Duration.ofSeconds(60),5);
  assertEquals("reader@example.com",service.verify(" reader "," READER@example.com ","123456"));
  verify(store).claim("password-reset","immutable-subject:reader@example.com","123456",5,true);
  service.consume("reader","reader@example.com");verifyNoMoreInteractions(store);
 }
 @Test void mismatchedEmailCannotRedeem() {
  var users=mock(AppUserMapper.class);var store=mock(AtomicChallengeStore.class);
  var user=new AppUser("reader","encoded","Reader","reader@example.com",UserRole.USER);when(users.findByUsername("reader")).thenReturn(user);
  var service=new PasswordResetCodeServiceImpl(mock(JavaMailSender.class),mock(StringRedisTemplate.class),users,store,"test@example.com",Duration.ofMinutes(10),Duration.ofSeconds(60),5);
  assertThrows(IllegalArgumentException.class,()->service.verify("reader","other@example.com","123456"));verifyNoInteractions(store);
 }
}
