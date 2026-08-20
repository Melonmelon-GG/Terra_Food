package com.dayan.food.controller;

import com.dayan.food.entity.vo.AuthUserPageVO;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.FoodPageVO;
import com.dayan.food.entity.enums.FoodReviewStatus;
import com.dayan.food.entity.dto.FoodReviewDTO;
import com.dayan.food.entity.dto.UserActiveUpdateDTO;
import com.dayan.food.service.AppUserService;
import com.dayan.food.service.FoodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AppUserService appUserService;
    private final FoodService foodService;

    public AdminController(AppUserService appUserService, FoodService foodService) {
        this.appUserService = appUserService;
        this.foodService = foodService;
    }

    @GetMapping("/foods")
    public FoodPageVO foods(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) FoodReviewStatus status
    ) {
        return foodService.listForAdmin(page, pageSize, status);
    }

    @PatchMapping("/foods/{id}/review")
    public void reviewFood(
            @PathVariable Long id,
            @RequestBody @Valid FoodReviewDTO request,
            Authentication authentication
    ) {
        foodService.review(id, request.status(), authentication.getName());
    }

    @GetMapping("/users")
    public AuthUserPageVO users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        var users = appUserService.listUsers(page, pageSize);
        int total = appUserService.countUsers();
        return new AuthUserPageVO(users, total, Math.max(1, page), clampPageSize(pageSize));
    }

    private static int clampPageSize(int pageSize) {
        return switch (pageSize) {
            case 20, 50 -> pageSize;
            default -> 10;
        };
    }

    @PatchMapping("/users/{id}/active")
    public void setActive(
            @PathVariable Long id,
            @RequestBody @Valid UserActiveUpdateDTO request,
            Authentication authentication
    ) {
        appUserService.setActive(id, request.active(), authentication.getName());
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id, Authentication authentication) {
        appUserService.deleteById(id, authentication.getName());
    }
}
