package com.dayan.food.service.impl;
import com.dayan.food.service.AtomicChallengeStore;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CaptchaServiceImplTests {
 @Test void issuanceUsesGenerationStoreAndVerificationConsumesAtomically() {
  var store=mock(AtomicChallengeStore.class);var service=new CaptchaServiceImpl(store,Duration.ofMinutes(5),3);
  var captcha=service.issue();
  assertNotNull(captcha.captchaId());
  verify(store).issue(eq("captcha"),eq(captcha.captchaId()),anyString(),eq(Duration.ofMinutes(5)));
  service.verify(captcha.captchaId()," 12 ");
  verify(store).claim("captcha",captcha.captchaId(),"12",3,false);
 }
 @Test void blankInputNeverCallsStore() {
  var store=mock(AtomicChallengeStore.class);var service=new CaptchaServiceImpl(store,Duration.ofMinutes(5),3);
  assertThrows(IllegalArgumentException.class,()->service.verify("", ""));verifyNoInteractions(store);
 }
}
