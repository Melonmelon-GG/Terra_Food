package com.dayan.food.service.impl;

import com.dayan.food.entity.vo.ImageExportVO;
import com.dayan.food.service.FoodImageExportService;
import com.dayan.food.service.FoodService;
import com.dayan.food.service.ImageStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FoodImageExportServiceImpl implements FoodImageExportService {

    private final FoodService foodService;
    private final ImageStorageService imageStorageService;

    public FoodImageExportServiceImpl(FoodService foodService, ImageStorageService imageStorageService) {
        this.foodService = foodService;
        this.imageStorageService = imageStorageService;
    }

    @Override
    public ImageExportVO read(Long foodId) {
        String imageUrl = foodService.detail(foodId).imageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "菜品没有可导出的图片");
        }
        if (!imageUrl.startsWith("/uploads/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "菜品图片不是系统上传文件");
        }
        return imageStorageService.readForExport(imageUrl);
    }
}
