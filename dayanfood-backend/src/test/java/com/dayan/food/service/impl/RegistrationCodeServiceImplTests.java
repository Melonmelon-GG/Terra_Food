package com.dayan.food.service.impl;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.*;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RegistrationCodeServiceImplTests {
 @Test void mailBindsNormalizedAddressAndAtomicGeneration() {
  var mail=mock(JavaMailSender.class);var redis=mock(StringRedisTemplate.class);var users=mock(AppUserMapper.class);
  var store=mock(AtomicChallengeStore.class);var captcha=mock(CaptchaService.class);
  ValueOperations<String,String> values=mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);
  when(values.setIfAbsent(anyString(),anyString(),any(Duration.class))).thenReturn(true);
  var service=new RegistrationCodeServiceImpl(mail,redis,users,store,captcha,"test@example.com",Duration.ofMinutes(10),Duration.ofSeconds(60),5);
  service.sendCode(" User@Example.COM ","challenge","12");
  verify(captcha).verify("challenge","12");
  var code=ArgumentCaptor.forClass(String.class);
  verify(store).issue(eq("registration"),eq("user@example.com"),code.capture(),eq(Duration.ofMinutes(10)));
  var message=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail).send(message.capture());
  assertEquals("user@example.com",message.getValue().getTo()[0]);assertTrue(message.getValue().getText().contains(code.getValue()));
  assertEquals("user@example.com",service.verify(" USER@example.com ","123456"));
  verify(store).claim("registration","user@example.com","123456",5,true);
  service.consume("user@example.com");verify(redis,never()).delete(anyCollection());
 }
 @Test void failedDeliveryRevokesOnlyIssuedGeneration() {
  var mail=mock(JavaMailSender.class);var redis=mock(StringRedisTemplate.class);var users=mock(AppUserMapper.class);var store=mock(AtomicChallengeStore.class);
  ValueOperations<String,String> values=mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);when(values.setIfAbsent(anyString(),anyString(),any(Duration.class))).thenReturn(true);
  when(store.issue(anyString(),anyString(),anyString(),any(Duration.class))).thenReturn("generation-1");
  doThrow(new MailSendException("unavailable")).when(mail).send(any(SimpleMailMessage.class));
  var service=new RegistrationCodeServiceImpl(mail,redis,users,store,mock(CaptchaService.class),"test@example.com",Duration.ofMinutes(10),Duration.ofSeconds(60),5);
  assertThrows(RegistrationCodeDeliveryException.class,()->service.sendCode("user@example.com","challenge","1"));
  verify(store).revoke("registration","user@example.com","generation-1");
 }
}
