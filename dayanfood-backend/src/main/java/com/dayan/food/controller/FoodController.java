package com.dayan.food.controller;

import com.dayan.food.entity.dto.FoodCreateDTO;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.FoodImportResultVO;
import com.dayan.food.service.FoodImportService;
import com.dayan.food.service.FoodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodService foodService;
    private final FoodImportService foodImportService;

    public FoodController(FoodService foodService, FoodImportService foodImportService) {
        this.foodService = foodService;
        this.foodImportService = foodImportService;
    }

    @GetMapping
    public List<FoodVO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long regionId
    ) {
        return foodService.list(keyword, regionId);
    }

    @GetMapping("/{id}")
    public FoodVO detail(@PathVariable Long id, Authentication authentication) {
        foodService.recordVisit(id, authentication == null ? null : authentication.getName());
        return foodService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoodVO create(@Valid @RequestBody FoodCreateDTO request, Authentication authentication) {
        return foodService.create(
                request.name(),
                request.regionId(),
                request.latitude(),
                request.longitude(),
                request.address(),
                request.summary(),
                request.story(),
                request.ingredients(),
                request.imageUrl(),
                authentication == null ? "无名" : authentication.getName()
        );
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public FoodImportResultVO importSpreadsheet(@RequestParam("file") MultipartFile file) {
        return foodImportService.importSpreadsheet(file);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        foodService.delete(id);
    }
}
