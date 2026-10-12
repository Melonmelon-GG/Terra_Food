package com.dayan.food.service.impl;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.ProfileStats;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.ProfileStatsMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileStatsServiceImplTests extends com.dayan.food.security.ActorTestSupport {
    private final AppUserMapper users = mock(AppUserMapper.class);
    private final ProfileStatsMapper stats = mock(ProfileStatsMapper.class);
    private final ProfileStatsServiceImpl service = new ProfileStatsServiceImpl(users, stats);

    @Test
    void returnsFullCountsForTheAuthenticatedUserWithoutRecentHistoryLimit() {
        AppUser user = mock(AppUser.class);
        when(user.isActive()).thenReturn(true);
        when(user.getId()).thenReturn(42L);
        actor(users, "explorer", user);
        ProfileStats counts = new ProfileStats();
        counts.setViewedFoodCount(1234);
        counts.setFavoriteCount(87);
        when(stats.findByUserId(42L)).thenReturn(counts);

        var result = service.getMine("explorer");

        assertEquals(1234, result.viewedFoodCount());
        assertEquals(87, result.favoriteCount());
        verify(stats).findByUserId(42L);
    }

    @Test
    void missingUserCannotReadStatistics() {
        var error = assertThrows(ResponseStatusException.class, () -> service.getMine("missing"));
        assertEquals(401, error.getStatusCode().value());
        verifyNoInteractions(stats);
    }

    @Test
    void disabledUserCannotReadStatistics() {
        actor(users, "disabled", mock(AppUser.class));
        var error = assertThrows(ResponseStatusException.class, () -> service.getMine("disabled"));
        assertEquals(401, error.getStatusCode().value());
        verifyNoInteractions(stats);
    }
}
