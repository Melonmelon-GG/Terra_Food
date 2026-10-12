package com.dayan.food.cache;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Bounded public-only cache. It never stores session or private account data. */
@Component
public class DiscoveryCache {
 private final DiscoveryVersion versions;
 private final boolean enabled;
 private final Semaphore requests=new Semaphore(32,true);
 private final Semaphore origin = new Semaphore(8,true);
 private final Map<String,Entry> cache = new LinkedHashMap<>(256,0.75f,true);
 private final ConcurrentHashMap<String,CompletableFuture<Object>> inflight = new ConcurrentHashMap<>();
 private record Entry(long expires,Object value){}
 public DiscoveryCache(DiscoveryVersion versions,@Value("${app.discovery-cache.enabled:false}") boolean enabled){this.versions=versions;this.enabled=enabled;}
 public long version(){return versions.current();}
 public <T>T get(String query,boolean admitted,Supplier<T> loader){
  if(!requests.tryAcquire())throw busy();
  try{return guarded(query,admitted,loader);}finally{requests.release();}
 }
 private <T>T guarded(String query,boolean admitted,Supplier<T> loader){
  for(int attempt=0;attempt<2;attempt++){
   long version=versions.current(); String key=version+":"+digest(query);
   Entry entry=null;
   if(enabled&&admitted){synchronized(cache){entry=cache.get(key);}}
   if(entry!=null&&entry.expires()>System.nanoTime()&&versions.current()==version){@SuppressWarnings("unchecked") T hit=(T)entry.value();return hit;}
   T result=load(key,loader);
   if(versions.current()!=version)continue;
   if(enabled&&admitted){synchronized(cache){cache.put(key,new Entry(System.nanoTime()+TimeUnit.SECONDS.toNanos(30),result));
    while(cache.size()>256)cache.remove(cache.keySet().iterator().next());}}
   return result;
  }
  throw new ResponseStatusException(HttpStatus.CONFLICT,"公开数据正在更新，请重试");
 }
 private <T>T load(String key,Supplier<T> loader){
  CompletableFuture<Object> mine=new CompletableFuture<>();
  CompletableFuture<Object> existing=inflight.putIfAbsent(key,mine);
  if(existing!=null){try{@SuppressWarnings("unchecked") T result=(T)existing.get(2,TimeUnit.SECONDS);return result;}
   catch(InterruptedException ex){Thread.currentThread().interrupt();throw busy();}
   catch(ExecutionException ex){if(ex.getCause() instanceof RuntimeException cause)throw cause;throw busy();}
   catch(TimeoutException ex){throw busy();}}
  boolean acquired=false;
  try{
   // Unique random queries cannot allocate unbounded waiters or cache entries.
   if(inflight.size()>32||!origin.tryAcquire(200,TimeUnit.MILLISECONDS))throw busy();
   acquired=true; T result=loader.get();mine.complete(result);return result;
  }catch(InterruptedException ex){Thread.currentThread().interrupt();mine.completeExceptionally(ex);throw busy();}
   catch(RuntimeException|Error ex){mine.completeExceptionally(ex);throw ex;}
  finally{if(acquired)origin.release();inflight.remove(key,mine);}
 }
 private String digest(String query){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(query.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private ResponseStatusException busy(){return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"公共查询繁忙，请稍后重试");}
}
