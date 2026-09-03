package com.dayan.food.config;

import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.service.AppUserService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DefaultAdminInitializerTests {

    @Test
    void createsOnlyTheAdminAccount() {
        AppUserService appUserService = mock(AppUserService.class);
        DefaultAdminInitializer initializer = new DefaultAdminInitializer(
                appUserService,
                "dayan_admin",
                "secure-password",
                "大炎管理员"
        );

        initializer.run(null);

        verify(appUserService).createIfMissing(
                "dayan_admin",
                "secure-password",
                "大炎管理员",
                UserRole.ADMIN
        );
        verifyNoMoreInteractions(appUserService);
    }

    @Test
    void rejectsAnEmptyAdminPassword() {
        AppUserService appUserService = mock(AppUserService.class);
        DefaultAdminInitializer initializer = new DefaultAdminInitializer(
                appUserService,
                "dayan_admin",
                "",
                "大炎管理员"
        );

        assertThrows(IllegalStateException.class, () -> initializer.run(null));
        verifyNoMoreInteractions(appUserService);
    }
}
