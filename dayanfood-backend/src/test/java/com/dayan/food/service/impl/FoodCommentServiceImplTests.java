package com.dayan.food.service.impl;

import com.dayan.food.entity.enums.FoodReviewStatus;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.po.FoodComment;
import com.dayan.food.entity.po.Region;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodCommentMapper;
import com.dayan.food.mapper.FoodMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodCommentServiceImplTests {

    @Mock
    private FoodCommentMapper foodCommentMapper;

    @Mock
    private FoodMapper foodMapper;

    @Mock
    private AppUserMapper appUserMapper;

    private FoodCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FoodCommentServiceImpl(foodCommentMapper, foodMapper, appUserMapper);
    }

    @Test
    void createTrimsContentAndReturnsPublicAuthorInformation() {
        AppUser author = user();
        when(foodMapper.findById(1L)).thenReturn(food());
        when(appUserMapper.findByUsername("reader")).thenReturn(author);
        when(foodCommentMapper.insert(org.mockito.ArgumentMatchers.any(FoodComment.class))).thenReturn(1);

        var result = service.create(1L, "  很有地方特色  ", "reader");

        ArgumentCaptor<FoodComment> captor = ArgumentCaptor.forClass(FoodComment.class);
        verify(foodCommentMapper).insert(captor.capture());
        assertEquals("很有地方特色", captor.getValue().getContent());
        assertEquals("reader", result.author().username());
        assertEquals("食客", result.author().displayName());
        assertEquals("/uploads/avatar.png", result.author().avatarUrl());
    }

    @Test
    void createRejectsMissingUserBeforeInsert() {
        when(foodMapper.findById(1L)).thenReturn(food());

        assertThrows(ResponseStatusException.class, () -> service.create(1L, "评论", "missing"));

        verify(foodCommentMapper, never()).insert(org.mockito.ArgumentMatchers.any(FoodComment.class));
    }

    @Test
    void listRejectsUnavailableFood() {
        assertThrows(ResponseStatusException.class, () -> service.list(404L));

        verify(foodCommentMapper, never()).findByFoodId(404L);
    }

    private AppUser user() {
        AppUser user = new AppUser("reader", "encoded", "食客", "reader@example.com", UserRole.USER);
        setAvatar(user, "/uploads/avatar.png");
        return user;
    }

    private void setAvatar(AppUser user, String avatarUrl) {
        try {
            var field = AppUser.class.getDeclaredField("avatarUrl");
            field.setAccessible(true);
            field.set(user, avatarUrl);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private Food food() {
        return new Food(
                "测试菜品",
                new Region("测试地区", "测试省份", ""),
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                "简介",
                "故事",
                "食材",
                null,
                null,
                "reader",
                FoodReviewStatus.APPROVED
        );
    }
}
