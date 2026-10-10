package com.dayan.food.security;

import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticatedActorTests extends ActorTestSupport {
    @Test void deletedAccountNeverFallsBackToSameUsernameReplacement() {
        var users = mock(AppUserMapper.class);
        var original = new AppUser("reused", "encoded", "A", UserRole.USER);
        actor(users, "reused", original);
        when(users.findByIdForUpdate(original.getId())).thenReturn(null);
        assertThrows(ResponseStatusException.class, () -> AuthenticatedActor.resolveForUpdate(users, "reused"));
        verify(users, never()).findByUsername(anyString());
    }

    @Test void revocationBetweenFilterAndServiceRejectsBeforeBusinessWrite() {
        var users = mock(AppUserMapper.class);
        var original = new AppUser("reader", "encoded", "A", UserRole.USER);
        actor(users, "reader", original);
        ReflectionTestUtils.setField(original, "authVersion", 2L);
        assertThrows(ResponseStatusException.class, () -> AuthenticatedActor.resolveForUpdate(users, "reader"));
        verify(users).findByIdForUpdate(original.getId());
        verify(users, never()).findByUsername(anyString());
    }

    @Test void subjectReplacementRejectedEvenIfIdIsRestored() {
        var users = mock(AppUserMapper.class);
        var original = new AppUser("reader", "encoded", "A", UserRole.USER);
        actor(users, "reader", original);
        ReflectionTestUtils.setField(original, "subjectId", "different-subject");
        assertThrows(ResponseStatusException.class, () -> AuthenticatedActor.resolve(users, "reader"));
    }
}
