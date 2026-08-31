package com.dayan.food.controller;

import com.dayan.food.entity.dto.AvatarUpdateDTO;
import com.dayan.food.entity.dto.DisplayNameUpdateDTO;
import com.dayan.food.entity.dto.FoodUpdateDTO;
import com.dayan.food.entity.dto.SignatureUpdateDTO;
import com.dayan.food.entity.vo.AuthUserVO;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.FoodFootprintVO;
import com.dayan.food.service.AppUserService;
import com.dayan.food.service.FoodService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/footprints")
    public List<FoodFootprintVO> listFootprints(
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication
    ) {
        return foodService.listRecentVisits(authentication.getName(), limit);
    }

    @PatchMapping("/avatar")
    public AuthUserVO updateAvatar(
            @Valid @RequestBody AvatarUpdateDTO request,
            Authentication authentication
    ) {
        return appUserService.updateAvatar(authentication.getName(), request.avatarUrl());
    }

    @PatchMapping("/signature")
    public AuthUserVO updateSignature(
            @Valid @RequestBody SignatureUpdateDTO request,
            Authentication authentication
    ) {
        return appUserService.updateSignature(authentication.getName(), request.signature());
    }

    @PatchMapping("/display-name")
    public AuthUserVO updateDisplayName(
            @Valid @RequestBody DisplayNameUpdateDTO request,
            Authentication authentication
    ) {
        return appUserService.submitDisplayName(authentication.getName(), request.displayName());
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
