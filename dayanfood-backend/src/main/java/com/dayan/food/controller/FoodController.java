package com.dayan.food.controller;

import com.dayan.food.entity.dto.FoodCreateDTO;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.service.FoodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    @GetMapping
    public List<FoodVO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long regionId
    ) {
        return foodService.list(keyword, regionId);
    }

    @GetMapping("/{id}")
    public FoodVO detail(@PathVariable Long id) {
        return foodService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoodVO create(@Valid @RequestBody FoodCreateDTO request) {
        return foodService.create(
                request.name(),
                request.regionId(),
                request.latitude(),
                request.longitude(),
                request.summary(),
                request.story(),
                request.ingredients(),
                request.imageUrl()
        );
    }
}
