package com.dayan.food.service.impl;

import com.dayan.food.service.AuthService;
import com.dayan.food.service.FoodService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoginPreloadServiceImplTests {

    @Test
    void preloadWarmsGlobalMapMarkers() {
        FoodService foodService = mock(FoodService.class);
        AuthService authService = mock(AuthService.class);

        new LoginPreloadServiceImpl(authService, foodService).preload("reader");

        verify(authService).currentUser("reader");
        verify(foodService).markers(null, null, null, null, null, null);
    }

    @Test
    void preloadFailureDoesNotBlockLogin() {
        FoodService foodService = mock(FoodService.class);
        AuthService authService = mock(AuthService.class);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(foodService).markers(null, null, null, null, null, null);

        assertDoesNotThrow(() -> new LoginPreloadServiceImpl(authService, foodService).preload("reader"));
    }
}
