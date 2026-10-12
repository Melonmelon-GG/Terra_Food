package com.dayan.food.service.impl;

import com.dayan.food.entity.enums.FoodReviewStatus;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.WishlistItem;
import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.po.Region;
import com.dayan.food.entity.vo.WishlistItemVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.mapper.WishlistMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishlistServiceImplTests {
    private static final long USER_ID = 7L;
    private static final String USERNAME = "reader";

    @Mock private WishlistMapper wishlistMapper;
    @Mock private AppUserMapper appUserMapper;
    @Mock private FoodMapper foodMapper;

    private WishlistServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WishlistServiceImpl(wishlistMapper, appUserMapper, foodMapper);
        com.dayan.food.support.TestActors.bind(appUserMapper,USERNAME,USER_ID,com.dayan.food.entity.enums.UserRole.USER);
    }

    @org.junit.jupiter.api.AfterEach void cleanup(){com.dayan.food.support.TestActors.clear();}

    @Test
    void listFindsSimilarApprovedDishesFromChineseKeywords() {
        WishlistItem item = mock(WishlistItem.class);
        when(item.getId()).thenReturn(12L);
        when(item.getContent()).thenReturn("想吃酸汤鱼");
        when(item.getSourceFoodId()).thenReturn(null);
        when(item.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(wishlistMapper.findPageByUserId(USER_ID,0,50)).thenReturn(List.of(item));
        List<Food> candidates = List.of(
                food(21L, "凯里酸汤鱼", "鱼、番茄、辣椒", "贵州", "凯里"),
                food(22L, "北京烤鸭", "鸭肉", "北京", "北京")
        );
        when(foodMapper.findMatchingCandidates(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(200)))
                .thenReturn(candidates);

        List<WishlistItemVO> result = service.list(USERNAME);

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().matches().size());
        assertEquals(21L, result.getFirst().matches().getFirst().food().id());
        assertTrue(result.getFirst().matches().getFirst().matchedFields().contains("NAME"));
    }

    @Test
    void emptyListDoesNotQueryFoodCandidates() {
        when(wishlistMapper.findPageByUserId(USER_ID,0,50)).thenReturn(List.of());

        assertTrue(service.list(USERNAME).isEmpty());

        verifyNoInteractions(foodMapper);
    }

    @Test
    void createRejectsDuplicateNormalizedContent() {
        when(wishlistMapper.insertIgnore(org.mockito.ArgumentMatchers.any(WishlistItem.class))).thenReturn(0);

        assertThrows(ResponseStatusException.class, () -> service.create("  酸汤鱼  ", null, USERNAME));
    }

    @Test
    void deleteIsScopedToCurrentUser() {
        when(wishlistMapper.deleteByIdAndUserId(9L, USER_ID)).thenReturn(1);

        service.delete(9L, USERNAME);

        verify(wishlistMapper).deleteByIdAndUserId(9L, USER_ID);
    }

    @Test
    void createDoesNotAcquireSecondConnectionForVersionCache() {
        var versions=mock(com.dayan.food.cache.DiscoveryVersion.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"versions",versions);
        when(wishlistMapper.insertIgnore(org.mockito.ArgumentMatchers.any(WishlistItem.class))).thenReturn(1);
        when(foodMapper.findMatchingCandidates(org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.eq(200))).thenReturn(List.of());
        assertTrue(service.create("酸汤鱼",null,USERNAME).matches().isEmpty());
        verifyNoInteractions(versions);
    }

    private static AppUser activeUser() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.isActive()).thenReturn(true);
        return user;
    }

    private static Food food(Long id, String name, String ingredients, String province, String region) {
        Food food = mock(Food.class); Region place = mock(Region.class);
        when(food.getId()).thenReturn(id); when(food.getName()).thenReturn(name);
        when(food.getIngredients()).thenReturn(ingredients); when(food.getSummary()).thenReturn(name + "简介");
        when(food.getStory()).thenReturn(""); when(food.getAddress()).thenReturn("");
        when(food.getLatitude()).thenReturn(BigDecimal.ZERO); when(food.getLongitude()).thenReturn(BigDecimal.ZERO);
        when(food.getHeat()).thenReturn(0); when(food.getReviewStatus()).thenReturn(FoodReviewStatus.APPROVED);
        when(food.getCreatedBy()).thenReturn("author"); when(food.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(place.getId()).thenReturn(id); when(place.getName()).thenReturn(region); when(place.getProvince()).thenReturn(province);
        when(food.getRegion()).thenReturn(place); return food;
    }
}
