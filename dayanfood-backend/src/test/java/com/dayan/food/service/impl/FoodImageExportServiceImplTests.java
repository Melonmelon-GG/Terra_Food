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

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodImageExportServiceImplTests {

    private static final long FOOD_ID = 8L;
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    @Mock
    private FoodService foodService;

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<java.io.InputStream> response;

    private FoodImageExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FoodImageExportServiceImpl(
                foodService,
                imageStorageService,
                httpClient,
                host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})},
                10 * 1024 * 1024
        );
    }

    @Test
    void delegatesUploadedImageToLocalStorage() {
        FoodVO food = foodWithImage("/uploads/dish.png");
        ImageExportVO expected = new ImageExportVO(PNG, "image/png");
        when(foodService.detail(FOOD_ID)).thenReturn(food);
        when(imageStorageService.readForExport("/uploads/dish.png")).thenReturn(expected);

        ImageExportVO actual = service.read(FOOD_ID);

        assertEquals(expected, actual);
        verifyNoInteractions(httpClient);
    }

    @Test
    void returnsValidatedRemoteImage() throws Exception {
        FoodVO food = foodWithImage("https://images.example.test/dish.png");
        when(foodService.detail(FOOD_ID)).thenReturn(food);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(PNG));
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<java.io.InputStream>>any()
        )).thenReturn(response);

        ImageExportVO actual = service.read(FOOD_ID);

        assertEquals("image/png", actual.contentType());
        assertArrayEquals(PNG, actual.content());
    }

    @Test
    void rejectsPrivateNetworkBeforeSendingRequest() throws Exception {
        service = new FoodImageExportServiceImpl(
                foodService,
                imageStorageService,
                httpClient,
                host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{127, 0, 0, 1})},
                10 * 1024 * 1024
        );
        FoodVO food = foodWithImage("http://private.example/dish.png");
        when(foodService.detail(FOOD_ID)).thenReturn(food);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.read(FOOD_ID));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(httpClient);
    }

    private FoodVO foodWithImage(String imageUrl) {
        FoodVO food = org.mockito.Mockito.mock(FoodVO.class);
        when(food.imageUrl()).thenReturn(imageUrl);
        return food;
    }
}
