package com.dayan.food.controller;

import com.dayan.food.entity.vo.UserPublicVO;
import com.dayan.food.service.UserPublicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserPublicController {

    private final UserPublicService userPublicService;

    public UserPublicController(UserPublicService userPublicService) {
        this.userPublicService = userPublicService;
    }

    @GetMapping("/{id}")
    public UserPublicVO profile(@PathVariable Long id) {
        return userPublicService.getProfile(id);
    }
}