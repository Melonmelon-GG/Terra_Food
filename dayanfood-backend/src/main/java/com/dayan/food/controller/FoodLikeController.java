package com.dayan.food.controller;

import com.dayan.food.entity.vo.FoodLikeStatusVO;
import com.dayan.food.service.FoodLikeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/foods/{foodId}/like")
public class FoodLikeController {

    private final FoodLikeService foodLikeService;

    public FoodLikeController(FoodLikeService foodLikeService) {
        this.foodLikeService = foodLikeService;
    }

    @GetMapping("/status")
    public FoodLikeStatusVO status(
            @PathVariable Long foodId,
            Authentication authentication
    ) {
        return foodLikeService.status(foodId, authentication == null ? null : authentication.getName());
    }

    @PostMapping
    public FoodLikeStatusVO like(
            @PathVariable Long foodId,
            Authentication authentication
    ) {
        return foodLikeService.like(foodId, authentication.getName());
    }

    @PostMapping("/unlike")
    public FoodLikeStatusVO unlike(
            @PathVariable Long foodId,
            Authentication authentication
    ) {
        return foodLikeService.unlike(foodId, authentication.getName());
    }
}