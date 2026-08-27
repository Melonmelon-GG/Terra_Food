package com.dayan.food.service.impl;

import com.dayan.food.entity.enums.FoodReviewStatus;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.po.FoodLike;
import com.dayan.food.entity.po.Region;
import com.dayan.food.entity.vo.FoodLikeStatusVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodLikeMapper;
import com.dayan.food.mapper.FoodMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodLikeServiceImplTests {

    private static final long FOOD_ID = 10L;
    private static final long USER_ID = 3L;
    private static final String USERNAME = "tester";

    @Mock
    private FoodLikeMapper foodLikeMapper;

    @Mock
    private FoodMapper foodMapper;

    @Mock
    private AppUserMapper appUserMapper;

    private FoodLikeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FoodLikeServiceImpl(foodLikeMapper, foodMapper, appUserMapper);
    }

    @Test
    void likeInsertsIgnoreAndReturnsStatus() {
        when(foodMapper.findById(FOOD_ID)).thenReturn(approvedFood());
        when(appUserMapper.findByUsername(USERNAME)).thenReturn(activeUser());
        when(foodLikeMapper.insertIgnore(anyLike())).thenReturn(1);
        when(foodLikeMapper.countByFoodId(FOOD_ID)).thenReturn(2);
        when(foodLikeMapper.exists(FOOD_ID, USER_ID)).thenReturn(1);

        FoodLikeStatusVO status = service.like(FOOD_ID, USERNAME);

        assertEquals(2, status.likeCount());
        assertTrue(status.likedByMe());

        ArgumentCaptor<FoodLike> captor = ArgumentCaptor.forClass(FoodLike.class);
        verify(foodLikeMapper).insertIgnore(captor.capture());
        assertEquals(FOOD_ID, captor.getValue().getFoodId());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    @Test
    void unlikeDeletesAndRefreshesStatus() {
        when(foodMapper.findById(FOOD_ID)).thenReturn(approvedFood());
        when(appUserMapper.findByUsername(USERNAME)).thenReturn(activeUser());
        when(foodLikeMapper.deleteIgnore(FOOD_ID, USER_ID)).thenReturn(1);
        when(foodLikeMapper.countByFoodId(FOOD_ID)).thenReturn(0);
        when(foodLikeMapper.exists(FOOD_ID, USER_ID)).thenReturn(0);

        FoodLikeStatusVO status = service.unlike(FOOD_ID, USERNAME);

        assertEquals(0, status.likeCount());
        assertEquals(false, status.likedByMe());
        verify(foodLikeMapper).deleteIgnore(FOOD_ID, USER_ID);
    }

    @Test
    void statusTreatsAnonymousAsNotLiked() {
        when(foodMapper.findById(FOOD_ID)).thenReturn(approvedFood());
        when(foodLikeMapper.countByFoodId(FOOD_ID)).thenReturn(5);

        FoodLikeStatusVO status = service.status(FOOD_ID, null);

        assertEquals(5, status.likeCount());
        assertEquals(false, status.likedByMe());
        verify(appUserMapper, never()).findByUsername(anyString());
    }

    @Test
    void likeRejectsMissingUserBeforeInsert() {
        when(foodMapper.findById(FOOD_ID)).thenReturn(approvedFood());
        when(appUserMapper.findByUsername(USERNAME)).thenReturn(null);

        assertThrows(ResponseStatusException.class, () -> service.like(FOOD_ID, USERNAME));
        verify(foodLikeMapper, never()).insertIgnore(anyLike());
    }

    @Test
    void likeRejectsUnavailableFood() {
        when(foodMapper.findById(FOOD_ID)).thenReturn(null);

        assertThrows(ResponseStatusException.class, () -> service.like(FOOD_ID, USERNAME));
        verify(foodLikeMapper, never()).insertIgnore(anyLike());
    }

    private static FoodLike anyLike() {
        return org.mockito.ArgumentMatchers.any(FoodLike.class);
    }

    private static Food approvedFood() {
        Region region = new Region("成都", "四川", "川菜");
        return new Food(
                "麻婆豆腐", region, new java.math.BigDecimal("30.5728"),
                new java.math.BigDecimal("104.0668"), "万福桥", "麻婆豆腐简介",
                "相传始于同治年间的川味经典。", "豆腐、牛肉末", null, null,
                "tester", FoodReviewStatus.APPROVED
        );
    }

    private static AppUser activeUser() {
        return new AppUser(USERNAME, "encoded", "Tester", "tester@example.com", UserRole.USER);
    }
}