package com.dayan.food.security;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Resolve the requesting account by its immutable session identity, never by a reusable login name. */
public final class AuthenticatedActor {
    private AuthenticatedActor() {}

    public static AppUserPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw revoked();
        }
        return principal;
    }

    public static AppUser resolve(AppUserMapper users, String suppliedUsername) {
        var principal = principal();
        if (!principal.username().equals(suppliedUsername)) throw revoked();
        boolean write = TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        // A writable service must acquire this lock before its first business query. Deletion,
        // revocation and role changes serialize against the same account row for this transaction.
        var user = write ? users.findByIdForUpdate(principal.userId()) : users.findById(principal.userId());
        return validate(user, principal);
    }

    public static AppUser resolveForUpdate(AppUserMapper users, String suppliedUsername) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Identity write guard requires a writable transaction");
        }
        return resolve(users, suppliedUsername);
    }

    public static AppUser validate(AppUser user, AppUserPrincipal principal) {
        if (user == null || !user.isActive() || !principal.subjectId().equals(user.getSubjectId())
                || principal.authVersion() != user.getAuthVersion() || principal.role() != user.getRole()) {
            throw revoked();
        }
        return user;
    }

    private static ResponseStatusException revoked() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "会话已失效，请重新登录");
    }
}
