package com.dayan.food.service.impl;

import com.dayan.food.entity.enums.SignatureStatus;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserServiceImplTests {

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AppUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppUserServiceImpl(appUserMapper, passwordEncoder);
    }

    @Test
    void updateSignatureStoresPendingText() {
        when(appUserMapper.updateSignature("tester", "有些滋味，值得反复回味。")).thenReturn(1);
        when(appUserMapper.findByUsername("tester")).thenReturn(user("tester", UserRole.USER));

        service.updateSignature("tester", "  有些滋味，值得反复回味。  ");

        verify(appUserMapper).updateSignature("tester", "有些滋味，值得反复回味。");
    }

    @Test
    void reviewSignatureApprovedAppliesPendingText() {
        when(appUserMapper.findByUsername("admin")).thenReturn(user("admin", UserRole.ADMIN));
        when(appUserMapper.findById(2L)).thenReturn(user("user", UserRole.USER));
        when(appUserMapper.approveSignature(2L)).thenReturn(1);

        service.reviewSignature(2L, SignatureStatus.APPROVED, "admin");

        verify(appUserMapper).approveSignature(2L);
    }

    @Test
    void reviewSignatureRejectsAndKeepsPendingText() {
        when(appUserMapper.findByUsername("admin")).thenReturn(user("admin", UserRole.ADMIN));
        when(appUserMapper.findById(2L)).thenReturn(user("user", UserRole.USER));
        when(appUserMapper.rejectSignature(2L)).thenReturn(1);

        service.reviewSignature(2L, SignatureStatus.REJECTED, "admin");

        verify(appUserMapper, never()).approveSignature(2L);
        verify(appUserMapper).rejectSignature(2L);
    }

    @Test
    void reviewSignatureRejectsInvalidStatus() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewSignature(2L, SignatureStatus.PENDING, "admin")
        );

        verify(appUserMapper, never()).approveSignature(2L);
        verify(appUserMapper, never()).rejectSignature(2L);
    }

    @Test
    void primaryAdminCanPromoteUserToSubAdmin() {
        when(appUserMapper.findByUsername("admin")).thenReturn(user("admin", UserRole.ADMIN));
        when(appUserMapper.findById(2L)).thenReturn(user("user", UserRole.USER));
        when(appUserMapper.updateRole(2L, UserRole.SUB_ADMIN)).thenReturn(1);

        service.setRole(2L, UserRole.SUB_ADMIN, "admin");

        verify(appUserMapper).updateRole(2L, UserRole.SUB_ADMIN);
    }

    @Test
    void subAdminCannotGrantRoles() {
        when(appUserMapper.findByUsername("helper")).thenReturn(user("helper", UserRole.SUB_ADMIN));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.setRole(2L, UserRole.SUB_ADMIN, "helper")
        );

        verify(appUserMapper, never()).updateRole(2L, UserRole.SUB_ADMIN);
    }

    @Test
    void subAdminCannotDeletePrimaryAdmin() {
        when(appUserMapper.findByUsername("helper")).thenReturn(user("helper", UserRole.SUB_ADMIN));
        when(appUserMapper.findById(1L)).thenReturn(user("admin", UserRole.ADMIN));

        assertThrows(IllegalArgumentException.class, () -> service.deleteById(1L, "helper"));

        verify(appUserMapper, never()).deleteById(1L);
    }

    @Test
    void listUsersClampsExtremePageToAvoidOffsetOverflow() {
        when(appUserMapper.findPage(199_990, 10)).thenReturn(List.of());

        service.listUsers(Integer.MAX_VALUE, 10);

        verify(appUserMapper).findPage(199_990, 10);
    }

    private AppUser user(String username, UserRole role) {
        return new AppUser(username, "encoded-password", username, role);
    }
}
