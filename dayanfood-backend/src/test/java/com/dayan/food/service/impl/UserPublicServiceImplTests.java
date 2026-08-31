package com.dayan.food.service.impl;

import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.vo.UserPublicVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPublicServiceImplTests {

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private FoodMapper foodMapper;

    private UserPublicServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserPublicServiceImpl(appUserMapper, foodMapper);
    }

    @Test
    void getProfileReturnsPublicSummaryWithoutPassword() {
        AppUser user = user(3L, "tester", true);
        ReflectionTestUtils.setField(user, "signature", "  人间烟火气  ");
        when(appUserMapper.findPublicById(3L)).thenReturn(user);
        when(foodMapper.findApprovedByCreatedBy("tester", 100)).thenReturn(List.of());

        UserPublicVO profile = service.getProfile(3L);

        verify(appUserMapper).findPublicById(3L);
        assertEquals("tester", profile.username());
        assertEquals("人间烟火气", profile.signature());
        assertEquals(0, profile.foods().size());
    }

    @Test
    void blankSignatureIsHiddenFromPublicProfile() {
        AppUser user = user(3L, "tester", true);
        when(appUserMapper.findPublicById(3L)).thenReturn(user);
        when(foodMapper.findApprovedByCreatedBy("tester", 100)).thenReturn(List.of());

        UserPublicVO profile = service.getProfile(3L);

        assertNull(profile.signature());
    }

    @Test
    void inactiveUserIsNotExposed() {
        AppUser user = user(3L, "tester", false);
        when(appUserMapper.findPublicById(3L)).thenReturn(user);

        assertThrows(ResponseStatusException.class, () -> service.getProfile(3L));
    }

    @Test
    void missingUserReturnsNotFound() {
        when(appUserMapper.findPublicById(99L)).thenReturn(null);

        assertThrows(ResponseStatusException.class, () -> service.getProfile(99L));
    }

    private AppUser user(Long id, String username, boolean active) {
        AppUser user = new AppUser(username, "encoded-password", username, UserRole.USER);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "active", active);
        return user;
    }
}