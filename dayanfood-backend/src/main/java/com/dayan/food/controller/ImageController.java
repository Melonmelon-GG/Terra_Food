package com.dayan.food.controller;

import com.dayan.food.entity.vo.ImageUploadVO;
import com.dayan.food.service.FoodImageExportService;
import com.dayan.food.service.ImageStorageService;
import com.dayan.food.mapper.ImageAssetMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageStorageService imageStorageService;
    private final ImageAssetMapper imageAssetMapper;
    private final FoodImageExportService foodImageExportService;

    public ImageController(
            ImageStorageService imageStorageService,
            ImageAssetMapper imageAssetMapper,
            FoodImageExportService foodImageExportService
    ) {
        this.imageStorageService = imageStorageService;
        this.imageAssetMapper = imageAssetMapper;
        this.foodImageExportService = foodImageExportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadVO upload(@RequestParam("file") MultipartFile file) {
        String url = imageStorageService.store(file);
        var asset = imageAssetMapper.findByOriginalUrl(url);
        return new ImageUploadVO(url, asset.getId(), asset.getOriginalWidth(), asset.getOriginalHeight(), asset.getStatus());
    }

    @GetMapping("/foods/{foodId}/export")
    public ResponseEntity<byte[]> exportFoodImage(@PathVariable Long foodId) {
        var image = foodImageExportService.read(foodId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.content().length)
                .body(image.content());
    }
}
