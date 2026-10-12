package com.dayan.food.security;

import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Unit fixtures only: real transaction/lock behavior is covered by the isolated HTTP suite. */
public class ActorTestSupport {
    public static void actor(AppUserMapper users, String username, AppUser user) {
        if (Mockito.mockingDetails(user).isMock()) {
            if (user.getId() == null) Mockito.lenient().when(user.getId()).thenReturn(1L);
            Long userId = user.getId();
            Mockito.lenient().when(user.getUsername()).thenReturn(username);
            Mockito.lenient().when(user.getSubjectId()).thenReturn("test-subject-" + userId);
            Mockito.lenient().when(user.getAuthVersion()).thenReturn(1L);
            if (user.getRole() == null) Mockito.lenient().when(user.getRole()).thenReturn(UserRole.USER);
        } else {
            if (user.getId() == null) ReflectionTestUtils.setField(user, "id", 1L);
            ReflectionTestUtils.setField(user, "subjectId", "test-subject-" + user.getId());
            ReflectionTestUtils.setField(user, "authVersion", 1L);
        }
        var principal = AppUserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        Mockito.lenient().when(users.findById(principal.userId())).thenReturn(user);
        Mockito.lenient().when(users.findByIdForUpdate(principal.userId())).thenReturn(user);
    }

    @AfterEach public void clearActor() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.clear();
    }
}
