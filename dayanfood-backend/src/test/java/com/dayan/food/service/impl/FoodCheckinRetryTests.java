package com.dayan.food.service.impl;
import com.dayan.food.mapper.*;
import com.dayan.food.entity.po.*;
import com.dayan.food.entity.enums.*;
import com.dayan.food.entity.dto.FoodCheckinCreateDTO;
import com.dayan.food.service.FoodCommentService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
class FoodCheckinRetryTests {
 final FoodCheckinMapper checkins=mock(FoodCheckinMapper.class);
 final AppUserMapper users=mock(AppUserMapper.class);
 final FoodMapper foods=mock(FoodMapper.class);
 final FoodCommentMapper commentMapper=mock(FoodCommentMapper.class);
 final FoodCommentService comments=mock(FoodCommentService.class);
 final FoodCheckinServiceImpl service=new FoodCheckinServiceImpl(checkins,foods,users,comments,commentMapper);
 final FoodCheckinCreateDTO request=new FoodCheckinCreateDTO(LocalDate.of(2025,1,1),"note","PRIVATE","Asia/Shanghai");
 @BeforeEach void setup(){
  com.dayan.food.support.TestActors.bind(users,"reader",7L,UserRole.USER);
  var food=mock(Food.class);when(food.getReviewStatus()).thenReturn(FoodReviewStatus.APPROVED);when(foods.findById(1L)).thenReturn(food);
  // Capture the canonical hash from a previous identical logical request.
  when(checkins.findIdempotentHash(7L,"retry-key")).thenAnswer(call->hash());
 }
 private String hash(){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("1\n2025-01-01\nnote\nPRIVATE\nAsia/Shanghai".getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new RuntimeException(e);}}
 @AfterEach void cleanup(){com.dayan.food.support.TestActors.clear();}
 @Test void deletedResultIsTombstonedAndNeverRepublished(){
  var failure=assertThrows(ResponseStatusException.class,()->service.create(1L,request,"reader","retry-key"));
  assertEquals(410,failure.getStatusCode().value());verify(checkins,never()).insert(any());verifyNoInteractions(comments);
 }
 @Test void winnerReplayUsesCurrentReadInsteadOfOldRepeatableSnapshot(){
  var existing=new FoodCheckin(1L,7L,"dish",request.eatenOn(),"note","PRIVATE",null,"Asia/Shanghai");
  ReflectionTestUtils.setField(existing,"id",42L);
  when(checkins.findIdempotentResult(7L,"retry-key",hash())).thenReturn(42L);
  when(checkins.findOwnedForUpdate(42L,7L)).thenReturn(existing);
  assertEquals(42L,service.create(1L,request,"reader","retry-key").id());
  verify(checkins,never()).findOwned(42L,7L);verify(checkins,never()).insert(any());
  var order=inOrder(users,foods);order.verify(users).findByIdForUpdate(7L);order.verify(foods).findById(1L);
 }
}
