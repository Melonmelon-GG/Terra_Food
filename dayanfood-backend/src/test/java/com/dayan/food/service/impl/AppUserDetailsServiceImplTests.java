package com.dayan.food.service.impl;

import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceImplTests {

    @Mock
    private AppUserMapper appUserMapper;

    private AppUserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppUserDetailsServiceImpl(appUserMapper);
    }

    @Test
    void emailLoginReturnsCanonicalUsernameAndRole() {
        AppUser user = new AppUser(
                "tester",
                "encoded-password",
                "Tester",
                "tester@example.com",
                UserRole.USER
        );
        when(appUserMapper.findByUsernameOrEmail("tester@example.com")).thenReturn(user);

        UserDetails details = service.loadUserByUsername("  tester@example.com  ");

        assertEquals("tester", details.getUsername());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_USER")));
        verify(appUserMapper).findByUsernameOrEmail("tester@example.com");
    }
}
