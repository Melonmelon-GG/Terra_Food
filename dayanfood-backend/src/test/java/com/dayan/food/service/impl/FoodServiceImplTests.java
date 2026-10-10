package com.dayan.food.service.impl;

import com.dayan.food.cache.CacheInvalidator;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.mapper.RegionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
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

    @Mock
    private Cache detailCache;

    private final CacheInvalidator cacheInvalidator = new CacheInvalidator();

    private FoodServiceImpl service;

    @BeforeEach
    void setUp() {
        com.dayan.food.support.TestActors.bind(appUserMapper,"reader",1L,com.dayan.food.entity.enums.UserRole.USER);
        service = new FoodServiceImpl(foodMapper, regionMapper, appUserMapper, cacheManager, cacheInvalidator);
    }

    @org.junit.jupiter.api.AfterEach void cleanup(){com.dayan.food.support.TestActors.clear();}

    @Test
    void repeatedDailyVisitRefreshesFootprintWithoutIncreasingHeat() {
        when(foodMapper.insertDailyVisit(7L, 1L)).thenReturn(0);

        service.recordVisit(7L, "reader");

        verify(foodMapper).touchDailyVisit(7L, 1L);
        verify(foodMapper, never()).incrementHeat(7L);
    }

    @Test
    void createsFoodWithoutLookingUpOrCreatingRegion() {
        var reader = new com.dayan.food.entity.po.AppUser("reader", "unused", "Reader",
                com.dayan.food.entity.enums.UserRole.USER);
        org.springframework.test.util.ReflectionTestUtils.setField(reader, "id", 1L);

        var result = service.create("Dish", null, java.math.BigDecimal.ONE,
                java.math.BigDecimal.TEN, "Address", "Summary", "Story", "Ingredients",
                null, null, "reader");
        org.junit.jupiter.api.Assertions.assertNull(result.region().id());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.dayan.food.entity.enums.FoodReviewStatus.PENDING, result.reviewStatus());
        org.mockito.Mockito.verifyNoInteractions(regionMapper);
        verify(foodMapper).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void storesCityOnFoodWithoutCreatingRegion() {
        var reader = new com.dayan.food.entity.po.AppUser("reader", "unused", "Reader",
                com.dayan.food.entity.enums.UserRole.USER);
        org.springframework.test.util.ReflectionTestUtils.setField(reader, "id", 1L);

        var saved = new com.dayan.food.entity.po.Food("Dish", null,
                java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "", "Summary",
                "Story", "Ingredients", null, null, "reader",
                com.dayan.food.entity.enums.FoodReviewStatus.PENDING);
        org.mockito.Mockito.doAnswer(call -> {
            org.springframework.test.util.ReflectionTestUtils.setField((Object) call.getArgument(0), "id", 91L);
            return 1;
        }).when(foodMapper).insert(org.mockito.ArgumentMatchers.any());
        when(foodMapper.findOwnedById(91L, 1L)).thenReturn(saved);
        service.create(new com.dayan.food.entity.dto.FoodCreateDTO("Dish", null,
                java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "", "Summary",
                "Story", "Ingredients", null, null, " 四川省 ", " 成都市 "), "reader");
        verify(foodMapper).updateLocationLabels(91L, "四川省", "成都市");
        org.mockito.Mockito.verifyNoInteractions(regionMapper);
    }

    @Test
    void firstDailyVisitIncreasesHeatWithoutTouchingExistingFootprint() {
        when(foodMapper.insertDailyVisit(7L, 1L)).thenReturn(1);
        when(foodMapper.incrementHeat(7L)).thenReturn(1);
        when(cacheManager.getCache("foodDetails")).thenReturn(detailCache);

        service.recordVisit(7L, "reader");

        verify(foodMapper).incrementHeat(7L);
        verify(foodMapper, never()).touchDailyVisit(7L, 1L);
    }

    @Test
    void recordVisitEvictsOnlyDetailCacheNotListCache() {
        when(foodMapper.insertDailyVisit(7L, 1L)).thenReturn(1);
        when(foodMapper.incrementHeat(7L)).thenReturn(1);
        when(cacheManager.getCache("foodDetails")).thenReturn(detailCache);

        service.recordVisit(7L, "reader");

        // S1：热度变动只整体失效详情缓存；列表/目录缓存靠 TTL 收敛，避免高并发浏览打穿缓存。
        verify(detailCache).evict(7L);
        verify(cacheManager, never()).getCache("foodLists");
    }

    @Test
    void recommendationNormalizesLocationAndCapsLimit() {
        when(foodMapper.findAgentRecommendations(1L, "四川", "成都", true, 10))
                .thenReturn(List.of());

        service.recommend("reader", " 四川 ", " 成都 ", true, 99);

        verify(foodMapper).findAgentRecommendations(1L, "四川", "成都", true, 10);
    }

    @Test
    void markersPassFiltersWithBoundedLimit() {
        when(foodMapper.findMarkers("拉面", null, null, null, null, null, 500))
                .thenReturn(List.of());

        service.markers(" 拉面 ", null, null, null, null, null);

        verify(foodMapper).findMarkers("拉面", null, null, null, null, null, 500);
    }

    @Test
    void markersRejectPartialBounds() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.markers(null, null, java.math.BigDecimal.ONE, null, null, null)
        );
    }

    @Test
    void catalogClampsPageToAvailableRange() {
        when(foodMapper.countCatalog(null, null, null, null, null, null)).thenReturn(33);
        when(foodMapper.findCatalogPage(null, null, null, null, null, null, 30, 30))
                .thenReturn(List.of());

        var result = service.catalog(null, null, 5, 30);

        verify(foodMapper).findCatalogPage(null, null, null, null, null, null, 30, 30);
        org.junit.jupiter.api.Assertions.assertEquals(33, result.total());
        org.junit.jupiter.api.Assertions.assertEquals(2, result.page());
    }

    @Test
    void catalogNormalizesKeywordAndCapsPageSize() {
        when(foodMapper.countCatalog("拉面", null, null, null, null, null)).thenReturn(0);
        when(foodMapper.findCatalogPage("拉面", null, null, null, null, null, 0, 500))
                .thenReturn(List.of());

        var result = service.catalog(" 拉面 ", null, 0, 2000);

        verify(foodMapper).findCatalogPage("拉面", null, null, null, null, null, 0, 500);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.page());
    }
}
