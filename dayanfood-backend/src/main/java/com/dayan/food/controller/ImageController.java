package com.dayan.food.controller;

import com.dayan.food.entity.vo.ImageUploadVO;
import com.dayan.food.service.ImageStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageStorageService imageStorageService;

    public ImageController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadVO upload(@RequestParam("file") MultipartFile file) {
        return new ImageUploadVO(imageStorageService.store(file));
    }
}
