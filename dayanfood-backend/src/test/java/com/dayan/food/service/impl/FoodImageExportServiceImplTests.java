package com.dayan.food.service.impl;

import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.ImageExportVO;
import com.dayan.food.service.FoodService;
import com.dayan.food.service.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodImageExportServiceImplTests {

    private static final long FOOD_ID = 8L;

    @Mock
    private FoodService foodService;

    @Mock
    private ImageStorageService imageStorageService;

    private FoodImageExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FoodImageExportServiceImpl(foodService, imageStorageService);
    }

    @Test
    void readsTheFoodImageFromInternalUploadStorage() {
        FoodVO food = foodWithImage("/uploads/dish.png");
        ImageExportVO expected = new ImageExportVO(new byte[]{1, 2, 3}, "image/png");
        when(foodService.detail(FOOD_ID)).thenReturn(food);
        when(imageStorageService.readForExport("/uploads/dish.png")).thenReturn(expected);

        ImageExportVO actual = service.read(FOOD_ID);

        assertEquals(expected, actual);
        verify(imageStorageService).readForExport("/uploads/dish.png");
    }

    @Test
    void rejectsNonSystemImageAddresses() {
        FoodVO food = foodWithImage("https://images.example.test/dish.png");
        when(foodService.detail(FOOD_ID)).thenReturn(food);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.read(FOOD_ID));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(imageStorageService);
    }

    @Test
    void reportsFoodsWithoutImages() {
        FoodVO food = foodWithImage(null);
        when(foodService.detail(FOOD_ID)).thenReturn(food);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.read(FOOD_ID));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(imageStorageService);
    }

    private FoodVO foodWithImage(String imageUrl) {
        FoodVO food = mock(FoodVO.class);
        when(food.imageUrl()).thenReturn(imageUrl);
        return food;
    }
}
