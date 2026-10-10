package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.*;
import com.dayan.food.entity.enums.*;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.vo.*;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.security.AppUserPrincipal;
import com.dayan.food.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Spring transactions and independent pooled MySQL connections. Never enable on a live schema. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "app.initial-admin.enabled=false","app.upload.variant-processing-enabled=false",
 "app.upload.orphan-cleanup-enabled=false","app.discovery-cache.enabled=true",
 "spring.session.redis.namespace=terra-audit-business-test"})
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables({
 @EnabledIfEnvironmentVariable(named="RUN_AUDIT_BUSINESS_MYSQL_TESTS",matches="true"),
 @EnabledIfEnvironmentVariable(named="DB_URL",matches="jdbc:mysql://[^/]+/terra_audit_[^?]+.*")})
class SecurityBusinessMysqlTests {
 @Autowired JdbcTemplate jdbc;
 @Autowired AppUserMapper users;
 @Autowired FoodCheckinService checkins;
 @Autowired FoodTagService tags;
 @Autowired EtchingDesignService etchings;
 @Autowired FoodService foods;
 String run; AppUser owner,admin,other;
 @BeforeEach void fixtures(){
  String schema=jdbc.queryForObject("SELECT DATABASE()",String.class);
  assertTrue(schema!=null&&schema.startsWith("terra_audit_"),"Audit suite requires a dedicated terra_audit_ schema");
  assertEquals("REPEATABLE-READ",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));
  run=UUID.randomUUID().toString().replace("-","").substring(0,12);
  owner=user("u",UserRole.USER);admin=user("a",UserRole.ADMIN);other=user("o",UserRole.USER);
 }
 @AfterEach void clearContext(){SecurityContextHolder.clearContext();}
 AppUser user(String suffix,UserRole role){
  String username="audit"+run+suffix;
  jdbc.update("INSERT INTO app_user(username,password,display_name,role,active,subject_id,auth_version) VALUES(?,?,?,?,TRUE,?,0)",username,"unused-audit-password",username,role.name(),UUID.randomUUID().toString());
  return users.findByUsername(username);
 }
 long food(String suffix,FoodReviewStatus status,double latitude,double longitude){
  String name="audit"+run+suffix;
  jdbc.update("INSERT INTO food(name,latitude,longitude,summary,story,ingredients,created_by,created_by_user_id,review_status,ownership_status) VALUES(?,?,?,?,?,?,?,?,?,'VERIFIED')",name,latitude,longitude,"summary","story","ingredients",owner.getUsername(),owner.getId(),status.name());
  return jdbc.queryForObject("SELECT id FROM food WHERE name=?",Long.class,name);
 }
 <T>T as(AppUser actor,Supplier<T> work){
  var previous=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();var principal=AppUserPrincipal.from(actor);
  context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
  try{SecurityContextHolder.setContext(context);return work.get();}finally{SecurityContextHolder.setContext(previous);}
 }
 List<Object> together(Supplier<?> one,Supplier<?> two)throws Exception{
  var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   java.util.function.Function<Supplier<?>,Callable<Object>> task=work->()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("start timeout");try{return work.get();}catch(ResponseStatusException e){return e;}};
   Future<Object> a=pool.submit(task.apply(one)),b=pool.submit(task.apply(two));assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
   return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
  }
 }
 @Test void concurrentRetryHasOneResultAndDeletedRetryNeverRepublishes()throws Exception{
  long id=food("checkin",FoodReviewStatus.APPROVED,1,1);
  var request=new FoodCheckinCreateDTO(LocalDate.of(2025,1,1),"public audit note","PUBLIC","Asia/Shanghai");
  Supplier<FoodCheckinVO> create=()->as(owner,()->checkins.create(id,request,owner.getUsername(),"audit-key-"+run));
  var results=together(create,create);
  assertTrue(results.stream().allMatch(FoodCheckinVO.class::isInstance),results.toString());
  var first=(FoodCheckinVO)results.getFirst();assertEquals(first.id(),((FoodCheckinVO)results.get(1)).id());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM food_checkin WHERE food_id=?",Integer.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM food_comment WHERE food_id=?",Integer.class,id));
  as(owner,()->{checkins.deleteMine(first.id(),first.version(),owner.getUsername());return null;});
  assertEquals(410,assertThrows(ResponseStatusException.class,create::get).getStatusCode().value());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM food_checkin WHERE food_id=?",Integer.class,id));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM food_comment WHERE food_id=?",Integer.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM food_checkin_idempotency WHERE user_id=? AND checkin_id IS NULL",Integer.class,owner.getId()));
  as(owner,()->checkins.create(id,request,owner.getUsername(),"new-audit-key-"+run));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM food_checkin WHERE food_id=?",Integer.class,id));
 }
 @Test void tagLastSlotIsAtomicUnderRepeatableRead()throws Exception{
  for(int i=0;i<19;i++)jdbc.update("INSERT INTO food_tag(type,name,normalized_name,created_by) VALUES('TASTE',?,?,?)","seed"+run+i,"seed"+run+i,owner.getId());
  var results=together(()->as(owner,()->tags.create(new FoodTagCreateDTO("TASTE","last"+run+"a"),owner.getUsername())),()->as(owner,()->tags.create(new FoodTagCreateDTO("TASTE","last"+run+"b"),owner.getUsername())));
  assertEquals(1,results.stream().filter(FoodTagVO.class::isInstance).count());
  assertEquals(1,results.stream().filter(v->v instanceof ResponseStatusException e&&e.getStatusCode().value()==429).count());
  assertEquals(20,jdbc.queryForObject("SELECT COUNT(*) FROM food_tag WHERE created_by=?",Integer.class,owner.getId()));
 }
 @Test void etchingLastSlotIsAtomicUnderRepeatableRead()throws Exception{
  for(int i=0;i<11;i++)jdbc.update("INSERT INTO user_etching_design(user_id,name,layer_one_json,layer_two_json,selected,created_at,updated_at) VALUES(?,?,?, '[]',FALSE,NOW(),NOW())",owner.getId(),"seed"+i,"[]");
  var pixels=new ArrayList<String>(Collections.nCopies(169,""));pixels.set(0,"#112233");var request=new EtchingDesignDTO("audit",pixels);
  Supplier<Object> create=()->{try{return as(owner,()->etchings.create(owner.getUsername(),request));}catch(IllegalArgumentException e){return e;}};
  var results=together(create,create);assertEquals(1,results.stream().filter(EtchingDesignVO.class::isInstance).count());
  assertEquals(12,jdbc.queryForObject("SELECT COUNT(*) FROM user_etching_design WHERE user_id=?",Integer.class,owner.getId()));
 }
 FoodTagVO approved(String suffix){
  var created=as(admin,()->tags.create(new FoodTagCreateDTO("TASTE","tag"+run+suffix),admin.getUsername()));
  return as(admin,()->tags.adminUpdate(created.id(),new FoodTagAdminUpdateDTO("TASTE",created.name(),"APPROVED",null,created.version()),admin.getUsername()));
 }
 @Test void pendingFoodRelationsRequireOwnerOrAdmin(){
  long id=food("pending",FoodReviewStatus.PENDING,1,1);var tag=approved("visible");
  jdbc.update("INSERT INTO food_tag_link(food_id,tag_id) VALUES(?,?)",id,tag.id());
  assertEquals(404,assertThrows(ResponseStatusException.class,()->tags.forFood(id,null)).getStatusCode().value());
  assertEquals(404,assertThrows(ResponseStatusException.class,()->as(other,()->tags.forFood(id,other.getUsername()))).getStatusCode().value());
  assertEquals(1,as(owner,()->tags.forFood(id,owner.getUsername())).size());assertEquals(1,as(admin,()->tags.forFood(id,admin.getUsername())).size());
 }
 @Test void mergedAliasesAndOldIdsReachFinalCanonicalTag(){
  var a=approved("A");var b=approved("B");var c=approved("C");
  var renamed=as(admin,()->tags.adminUpdate(a.id(),new FoodTagAdminUpdateDTO("TASTE","renamed"+run,"APPROVED",null,a.version()),admin.getUsername()));
  as(admin,()->tags.merge(a.id(),b.id(),renamed.version(),admin.getUsername()));as(admin,()->tags.merge(b.id(),c.id(),b.version(),admin.getUsername()));
  assertEquals(List.of(c.id()),tags.canonicalIds(List.of(a.id(),b.id(),c.id())));
  assertEquals(c.id(),tags.list("TASTE",a.name()).getFirst().id());assertEquals(c.id(),tags.list("TASTE",renamed.name()).getFirst().id());
 }
 @Test void clusterMembersIntersectOriginalViewportAndVersionChangesOnDelete(){
  long id=food("map",FoodReviewStatus.APPROVED,1,1.1);food("mapoutside",FoodReviewStatus.APPROVED,1,2.1);
  var cluster=foods.mapClusters("audit"+run,null,List.of(),List.of(),List.of(),BigDecimal.ZERO,BigDecimal.valueOf(2),BigDecimal.ONE,BigDecimal.valueOf(1.5),4);
  assertEquals(1,cluster.total());
  // zoom=4 x=8 y=7: both fixture points are in this cell, only one is in the requested viewport.
  var members=foods.mapClusterMembers("4:8:7","audit"+run,null,List.of(),List.of(),List.of(),BigDecimal.ZERO,BigDecimal.valueOf(2),BigDecimal.ONE,BigDecimal.valueOf(1.5),1,20);
  assertEquals(1,members.total());assertEquals(id,members.items().getFirst().id());
  long version=jdbc.queryForObject("SELECT version FROM public_discovery_version WHERE id=1",Long.class);
  as(admin,()->{foods.delete(id);return null;});assertTrue(jdbc.queryForObject("SELECT version FROM public_discovery_version WHERE id=1",Long.class)>version);
  var after=foods.mapClusters("audit"+run,null,List.of(),List.of(),List.of(),BigDecimal.ZERO,BigDecimal.valueOf(2),BigDecimal.ONE,BigDecimal.valueOf(1.5),4);assertEquals(0,after.total());
 }
 @Test void exactGridBoundaryBelongsToOneCellAndAntimeridianViewportIsConsistent(){
  long boundary=food("boundary",FoodReviewStatus.APPROVED,1,0);
  var left=foods.mapClusterMembers("4:7:7","audit"+run,null,List.of(),List.of(),List.of(),BigDecimal.ZERO,BigDecimal.valueOf(2),BigDecimal.valueOf(-1),BigDecimal.ONE,1,20);
  var right=foods.mapClusterMembers("4:8:7","audit"+run,null,List.of(),List.of(),List.of(),BigDecimal.ZERO,BigDecimal.valueOf(2),BigDecimal.valueOf(-1),BigDecimal.ONE,1,20);
  assertEquals(0,left.total());assertEquals(1,right.total());assertEquals(boundary,right.items().getFirst().id());
  long east=food("east",FoodReviewStatus.APPROVED,1,180);food("west",FoodReviewStatus.APPROVED,1,-179);
  var crossing=foods.mapClusters("audit"+run,null,List.of(),List.of(),List.of(),BigDecimal.ZERO,BigDecimal.valueOf(2),BigDecimal.valueOf(170),BigDecimal.valueOf(-170),4);
  assertEquals(2,crossing.total());
  var eastMembers=foods.mapClusterMembers("4:15:7","audit"+run,null,List.of(),List.of(),List.of(),BigDecimal.ZERO,BigDecimal.valueOf(2),BigDecimal.valueOf(170),BigDecimal.valueOf(-170),1,20);
  assertEquals(1,eastMembers.total());assertEquals(east,eastMembers.items().getFirst().id());
 }

}
