package com.dayan.food.cache;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DiscoveryCacheTests {
 @Test void lateReadCannotPopulateNewVersionWithOldValue(){
  var versions=mock(DiscoveryVersion.class);var epoch=new java.util.concurrent.atomic.AtomicLong(1);
  when(versions.current()).thenAnswer(call->epoch.get());var cache=new DiscoveryCache(versions,true);
  var calls=new AtomicInteger();
  String value=cache.get("catalog",true,()->{if(calls.incrementAndGet()==1){epoch.incrementAndGet();return "old";}return "new";});
  assertEquals("new",value);assertEquals(2,calls.get());
  assertEquals("new",cache.get("catalog",true,()->{fail("must hit new version");return "invalid";}));
 }
 @Test void disabledCacheNeverReturnsSavedContent(){
  var versions=mock(DiscoveryVersion.class);when(versions.current()).thenReturn(1L);var cache=new DiscoveryCache(versions,false);
  assertEquals("first",cache.get("same",true,()->"first"));assertEquals("second",cache.get("same",true,()->"second"));
 }
 @Test void evictionFailureDoesNotTurnCommittedWriteIntoFailure(){
  var cache=mock(org.springframework.cache.Cache.class);doThrow(new IllegalStateException("offline")).when(cache).clear();
  assertDoesNotThrow(()->new CacheInvalidator().clear(cache));
 }
}
