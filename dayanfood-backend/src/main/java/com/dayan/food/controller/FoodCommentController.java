package com.dayan.food.controller;

import com.dayan.food.entity.dto.FoodCommentCreateDTO;
import com.dayan.food.entity.vo.FoodCommentVO;
import com.dayan.food.service.FoodCommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/foods/{foodId}/comments")
public class FoodCommentController {

    private final FoodCommentService foodCommentService;

    public FoodCommentController(FoodCommentService foodCommentService) {
        this.foodCommentService = foodCommentService;
    }

    @GetMapping
    public List<FoodCommentVO> list(@PathVariable Long foodId) {
        return foodCommentService.list(foodId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoodCommentVO create(
            @PathVariable Long foodId,
            @Valid @RequestBody FoodCommentCreateDTO request,
            Authentication authentication
    ) {
        return foodCommentService.create(foodId, request.content(), authentication.getName());
    }
}
