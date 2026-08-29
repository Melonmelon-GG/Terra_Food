package com.dayan.food.service.impl;

import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.mapper.RegionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodServiceImplTests {

    @Mock
    private FoodMapper foodMapper;

    @Mock
    private RegionMapper regionMapper;

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private CacheManager cacheManager;

    private FoodServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FoodServiceImpl(foodMapper, regionMapper, appUserMapper, cacheManager);
    }

    @Test
    void repeatedDailyVisitRefreshesFootprintWithoutIncreasingHeat() {
        when(foodMapper.insertDailyVisit(7L, "reader")).thenReturn(0);

        service.recordVisit(7L, "reader");

        verify(foodMapper).touchDailyVisit(7L, "reader");
        verify(foodMapper, never()).incrementHeat(7L);
    }

    @Test
    void firstDailyVisitIncreasesHeatWithoutTouchingExistingFootprint() {
        when(foodMapper.insertDailyVisit(7L, "reader")).thenReturn(1);
        when(foodMapper.incrementHeat(7L)).thenReturn(1);

        service.recordVisit(7L, "reader");

        verify(foodMapper).incrementHeat(7L);
        verify(foodMapper, never()).touchDailyVisit(7L, "reader");
    }

    @Test
    void recommendationNormalizesLocationAndCapsLimit() {
        when(foodMapper.findAgentRecommendations("reader", "四川", "成都", true, 10))
                .thenReturn(List.of());

        service.recommend("reader", " 四川 ", " 成都 ", true, 99);

        verify(foodMapper).findAgentRecommendations("reader", "四川", "成都", true, 10);
    }
}
