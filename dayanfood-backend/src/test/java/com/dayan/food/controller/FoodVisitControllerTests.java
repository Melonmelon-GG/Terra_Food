package com.dayan.food.controller;

import com.dayan.food.service.FoodCreationService;
import com.dayan.food.service.FoodImportService;
import com.dayan.food.service.FoodService;
import com.dayan.food.entity.vo.FoodVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FoodVisitControllerTests {
    private FoodService foods;
    private MockMvc http;
    private final UsernamePasswordAuthenticationToken user =
            new UsernamePasswordAuthenticationToken("fixture", "unused", java.util.List.of());

    @BeforeEach
    void setup() {
        foods = mock(FoodService.class);
        http = MockMvcBuilders.standaloneSetup(new FoodController(foods,
                mock(FoodImportService.class), mock(FoodCreationService.class))).build();
    }

    @Test
    void readOnlyDetailDoesNotRegisterAVisit() throws Exception {
        http.perform(get("/api/foods/1").principal(user).param("recordVisit", "false"))
                .andExpect(status().isOk());
        verify(foods).detail(1L);
        verify(foods, never()).recordVisit(anyLong(), anyString());
    }

    @Test
    void legacyDetailStillRegistersAuthenticatedVisits() throws Exception {
        http.perform(get("/api/foods/1").principal(user)).andExpect(status().isOk());
        var order = inOrder(foods);
        order.verify(foods).detail(1L);
        order.verify(foods).recordVisit(1L, "fixture");
        order.verify(foods).detail(1L);
    }

    @Test
    void explicitVisitChecksVisibilityBeforeWriting() throws Exception {
        when(foods.detail(1L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        http.perform(post("/api/foods/1/visits").principal(user)).andExpect(status().isNotFound());
        verify(foods, never()).recordVisit(anyLong(), anyString());
    }

    @Test
    void explicitVisitRequiresIdentityAndReturnsUpdatedHeat() throws Exception {
        http.perform(post("/api/foods/1/visits")).andExpect(status().isUnauthorized());
        verifyNoInteractions(foods);
        var food = mock(FoodVO.class);
        when(food.heat()).thenReturn(7);
        when(foods.detail(1L)).thenReturn(food);
        http.perform(post("/api/foods/1/visits").principal(user)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.heat").value(7));
        verify(foods).recordVisit(1L, "fixture");
    }
}
