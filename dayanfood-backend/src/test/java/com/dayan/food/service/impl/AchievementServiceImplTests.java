package com.dayan.food.service.impl;

import com.dayan.food.entity.po.Achievement;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AchievementMapper;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.EtchingDesignMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementServiceImplTests extends com.dayan.food.security.ActorTestSupport {

    @Mock
    private AchievementMapper achievementMapper;

    @Mock
    private AppUserMapper appUserMapper;
    @Mock
    private EtchingDesignMapper etchingDesignMapper;

    private AppUser user;

    @Mock
    private Achievement achievement;

    private AchievementServiceImpl service;

    @BeforeEach
    void setUp() {
        user = new AppUser("reader", "encoded", "reader", com.dayan.food.entity.enums.UserRole.USER);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 7L);
        actor(appUserMapper, "reader", user);
        service = new AchievementServiceImpl(achievementMapper, appUserMapper, etchingDesignMapper);
    }

    @Test
    void awardFirstLoginUnlocksConfiguredAchievementForUser() {
        when(achievementMapper.findByCode("FIRST_LOGIN")).thenReturn(achievement);
        when(achievement.getId()).thenReturn(3L);

        service.awardFirstLogin("reader");

        verify(achievementMapper).unlock(7L, 3L);
    }

    @Test
    void listUnnotifiedReturnsPublicAchievementView() {
        when(achievementMapper.findByCode("FIRST_LOGIN")).thenReturn(achievement);
        LocalDateTime unlockedAt = LocalDateTime.of(2026, 8, 25, 20, 0);
        when(achievementMapper.findUnnotifiedByUserId(7L)).thenReturn(List.of(achievement));
        when(achievement.getId()).thenReturn(3L);
        when(achievement.getCode()).thenReturn("FIRST_LOGIN");
        when(achievement.getName()).thenReturn("初入炎境");
        when(achievement.getDescription()).thenReturn("首次登录");
        when(achievement.getImageUrl()).thenReturn("/achievements/first-login.png");
        when(achievement.getUnlockedAt()).thenReturn(unlockedAt);

        var result = service.listUnnotified("reader");

        assertEquals(1, result.size());
        assertEquals("FIRST_LOGIN", result.getFirst().code());
        assertEquals(unlockedAt, result.getFirst().unlockedAt());
    }

    @Test
    void selectRejectsAchievementThatUserHasNotUnlocked() {
        when(achievementMapper.select("reader", 99L)).thenReturn(0);

        assertThrows(IllegalArgumentException.class, () -> service.select("reader", 99L));

        verify(achievementMapper).clearSelection("reader");
    }
}
