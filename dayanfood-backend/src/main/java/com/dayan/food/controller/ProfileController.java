package com.dayan.food.controller;

import com.dayan.food.entity.dto.AvatarUpdateDTO;
import com.dayan.food.entity.dto.FoodUpdateDTO;
import com.dayan.food.entity.vo.AuthUserVO;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.service.AppUserService;
import com.dayan.food.service.FoodService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final FoodService foodService;
    private final AppUserService appUserService;

    public ProfileController(FoodService foodService, AppUserService appUserService) {
        this.foodService = foodService;
        this.appUserService = appUserService;
    }

    @GetMapping("/foods")
    public List<FoodVO> listMine(Authentication authentication) {
        return foodService.listMine(authentication.getName());
    }

    @PatchMapping("/avatar")
    public AuthUserVO updateAvatar(
            @Valid @RequestBody AvatarUpdateDTO request,
            Authentication authentication
    ) {
        return appUserService.updateAvatar(authentication.getName(), request.avatarUrl());
    }

    @PatchMapping("/foods/{id}")
    public FoodVO updateMine(
            @PathVariable Long id,
            @Valid @RequestBody FoodUpdateDTO request,
            Authentication authentication
    ) {
        return foodService.updateMine(id, request, authentication.getName());
    }
}
