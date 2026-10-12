package com.dayan.food.support;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.security.AppUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.lenient;
/** Unit-test security context; transaction behavior is separately exercised by MySQL HTTP scenarios. */
public final class TestActors {
 public static AppUser bind(AppUserMapper mapper,String name,long id,UserRole role){
  AppUser user=new AppUser(name,"unused",name,role);
  ReflectionTestUtils.setField(user,"id",id);ReflectionTestUtils.setField(user,"subjectId","test-subject-"+id);
  use(user);lenient().when(mapper.findById(id)).thenReturn(user);lenient().when(mapper.findByIdForUpdate(id)).thenReturn(user);return user;
 }
 public static void use(AppUser user){
  var principal=AppUserPrincipal.from(user);
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
  TransactionSynchronizationManager.setActualTransactionActive(true);
  TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
 }
 public static void clear(){SecurityContextHolder.clearContext();TransactionSynchronizationManager.clear();}
}
