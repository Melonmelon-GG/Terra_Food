package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.FoodCreateDTO;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.FoodCreateIdempotency;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodIdempotencyMapper;
import com.dayan.food.service.FoodService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodCreationServiceImplTests {
    @Mock private FoodService foodService;
    @Mock private AppUserMapper users;
    @Mock private FoodIdempotencyMapper idempotency;

    private FoodCreationServiceImpl service;
    private FoodCreateDTO request;

    @BeforeEach
    void setUp() {
        com.dayan.food.support.TestActors.bind(users,"alice",7L,com.dayan.food.entity.enums.UserRole.USER);
        service = new FoodCreationServiceImpl(foodService, users, idempotency, new ObjectMapper());
        request = new FoodCreateDTO("Dish", 1L, BigDecimal.ONE, BigDecimal.TEN,
                "Address", "Summary", "Story", "Ingredients", null, null,
                "Province", "City", List.of());
    }

    @org.junit.jupiter.api.AfterEach void cleanup(){com.dayan.food.support.TestActors.clear();}

    @Test
    void locksAccountBeforeReadingAndCreatingIdempotentResult() {
        AppUser user = mock(AppUser.class);
        FoodVO created = mock(FoodVO.class);
        when(idempotency.find(7L, "request-key-123")).thenReturn(null);
        when(foodService.create(request, "alice")).thenReturn(created);
        when(created.id()).thenReturn(42L);
        when(idempotency.attachFood(7L, "request-key-123", 42L)).thenReturn(1);

        assertEquals(created, service.create(request, "alice", "request-key-123"));

        InOrder order = inOrder(users, idempotency, foodService);
        order.verify(users).findByIdForUpdate(7L);
        order.verify(idempotency).deleteExpired(eq(7L), eq("request-key-123"), any());
        order.verify(idempotency).find(7L, "request-key-123");
        order.verify(idempotency).insertReservation(eq(7L), eq("request-key-123"), any(), any());
        order.verify(foodService).create(request, "alice");
        order.verify(idempotency).attachFood(7L, "request-key-123", 42L);
    }

    @Test
    void rejectsReuseWithDifferentContentBeforeCreatingFood() {
        AppUser user = mock(AppUser.class);
        FoodCreateIdempotency existing = mock(FoodCreateIdempotency.class);
        when(idempotency.find(7L, "request-key-123")).thenReturn(existing);
        when(existing.getRequestHash()).thenReturn("different-hash");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(request, "alice", "request-key-123"));

        assertEquals(409, error.getStatusCode().value());
        verify(foodService, never()).create(any(FoodCreateDTO.class), any(String.class));
    }
}
