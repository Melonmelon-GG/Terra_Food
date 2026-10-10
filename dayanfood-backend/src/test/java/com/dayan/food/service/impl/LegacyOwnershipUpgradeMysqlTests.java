package com.dayan.food.service.impl;

import com.dayan.food.cache.CacheInvalidator;
import com.dayan.food.entity.enums.FoodReviewStatus;
import com.dayan.food.mapper.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

/** Upgrade only a dedicated, initially empty disposable schema. Does not drop or reset any schema. */
@EnabledIfEnvironmentVariable(named="MIGRATION_DB_URL",matches="jdbc:mysql://[^/]+/terra_audit_[^?]+.*")
class LegacyOwnershipUpgradeMysqlTests {
 @Test void historicalUsernameReuseIsQuarantinedAndLegacyAliasesAreRepaired()throws Exception{
  String url=System.getenv("MIGRATION_DB_URL");
  String username=System.getenv().getOrDefault("MIGRATION_DB_USERNAME",System.getenv("DB_USERNAME"));
  String password=System.getenv().getOrDefault("MIGRATION_DB_PASSWORD",System.getenv("DB_PASSWORD"));
  var ds=new DriverManagerDataSource(url,username,password);var jdbc=new JdbcTemplate(ds);
  assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("terra_audit_"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class),"Upgrade fixture requires an empty dedicated schema; do not clean a used schema to make this pass");
  Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).target("21").load().migrate();
  jdbc.update("INSERT INTO app_user(username,password,display_name,role) VALUES('reused-upgrade-name','unused','Original A','USER')");
  long oldId=jdbc.queryForObject("SELECT id FROM app_user WHERE username='reused-upgrade-name'",Long.class);
  jdbc.update("INSERT INTO food(name,latitude,longitude,summary,story,ingredients,created_by,review_status,created_at) VALUES('old A dish',1,1,'summary','story','ingredients','reused-upgrade-name','APPROVED','2000-01-01')");
  long foodId=jdbc.queryForObject("SELECT id FROM food WHERE name='old A dish'",Long.class);
  jdbc.update("DELETE FROM app_user WHERE id=?",oldId);
  jdbc.update("INSERT INTO app_user(username,password,display_name,role) VALUES('reused-upgrade-name','unused','New B','USER')");
  long newId=jdbc.queryForObject("SELECT id FROM app_user WHERE username='reused-upgrade-name'",Long.class);assertNotEquals(oldId,newId);
  jdbc.update("INSERT INTO food_tag(type,name,normalized_name,status) VALUES('TASTE','upgrade-target','upgrade-target','APPROVED')");
  long target=jdbc.queryForObject("SELECT id FROM food_tag WHERE name='upgrade-target'",Long.class);
  jdbc.update("INSERT INTO food_tag(type,name,normalized_name,status,merged_into_id) VALUES('TASTE','upgrade-old','upgrade-old','MERGED',?)",target);
  long source=jdbc.queryForObject("SELECT id FROM food_tag WHERE name='upgrade-old'",Long.class);
  jdbc.update("INSERT INTO food_tag_alias(tag_id,alias,normalized_alias) VALUES(?,'upgrade-alias','upgrade-alias')",source);
  Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).load().migrate();
  assertEquals("LEGACY_UNVERIFIED",jdbc.queryForObject("SELECT ownership_status FROM food WHERE id=?",String.class,foodId));
  // The V22 relationship is retained as evidence, but must no longer authorize the coincident new account.
  assertEquals(newId,jdbc.queryForObject("SELECT created_by_user_id FROM food WHERE id=?",Long.class,foodId));
  assertEquals(target,jdbc.queryForObject("SELECT tag_id FROM food_tag_alias WHERE normalized_alias='upgrade-alias'",Long.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM food_ownership_review",Integer.class),"No evidence review was fabricated");
  var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
  var configuration=new org.apache.ibatis.session.Configuration();configuration.setMapUnderscoreToCamelCase(true);factory.setConfiguration(configuration);
  factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
  try(var session=factory.getObject().openSession()){
   var foods=session.getMapper(FoodMapper.class);var users=session.getMapper(AppUserMapper.class);
   assertNull(foods.findOwnedById(foodId,newId));assertTrue(foods.findMinePage(newId,0,20).isEmpty());assertEquals(0,foods.countMine(newId));
   assertEquals(0,foods.updateOwnedDetails(foodId,newId,"Stolen",null,BigDecimal.ONE,BigDecimal.ONE,"","summary","story","ingredients",null,null,FoodReviewStatus.PENDING,null));
   var service=new FoodServiceImpl(foods,session.getMapper(RegionMapper.class),users,new ConcurrentMapCacheManager(),new CacheInvalidator());
   assertNull(service.detail(foodId).creator().id(),"Public snapshot must not link A's dish to current same-name B");
   session.rollback();
  }
  assertEquals("old A dish",jdbc.queryForObject("SELECT name FROM food WHERE id=?",String.class,foodId));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0",Integer.class));
 }
}
