package com.dayan.food.service.impl;
import com.dayan.food.mapper.*;
import com.dayan.food.cache.CacheInvalidator;
import com.dayan.food.entity.vo.FoodTagVO;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class FoodTagVisibilityTests {
 final FoodTagMapper tags=mock(FoodTagMapper.class);final AppUserMapper users=mock(AppUserMapper.class);
 final FoodTagServiceImpl service=new FoodTagServiceImpl(tags,users,new ConcurrentMapCacheManager(),new CacheInvalidator());
 @Test void anonymousCannotDiscoverPendingFoodRelations(){
  when(tags.canAccessFood(4L,null,false)).thenReturn(0);
  assertEquals(404,assertThrows(ResponseStatusException.class,()->service.forFood(4L,null)).getStatusCode().value());
  verify(tags,never()).findForFood(4L,null,false);
 }
 @Test void oldIdsFollowEntireCanonicalChain(){
  when(tags.findById(1L)).thenReturn(tag(1L,"MERGED",2L));when(tags.findById(2L)).thenReturn(tag(2L,"MERGED",3L));when(tags.findById(3L)).thenReturn(tag(3L,"APPROVED",null));
  assertEquals(List.of(3L),service.canonicalIds(List.of(1L,2L,3L)));
 }
 @Test void corruptCycleIsRejected(){
  when(tags.findById(1L)).thenReturn(tag(1L,"MERGED",2L));when(tags.findById(2L)).thenReturn(tag(2L,"MERGED",1L));
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.canonicalIds(List.of(1L))).getStatusCode().value());
 }
 FoodTagVO tag(long id,String status,Long target){return new FoodTagVO(id,"TASTE","t"+id,status,target,0,1L,null,null);}
}
