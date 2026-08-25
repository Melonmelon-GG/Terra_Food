package com.dayan.food.controller;

import com.dayan.food.entity.dto.AchievementSelectionDTO;
import com.dayan.food.entity.vo.AchievementVO;
import com.dayan.food.service.AchievementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @GetMapping("/me")
    public List<AchievementVO> listUnlocked(Authentication authentication) {
        return achievementService.listUnlocked(authentication.getName());
    }

    @GetMapping("/notifications")
    public List<AchievementVO> listNotifications(Authentication authentication) {
        return achievementService.listUnnotified(authentication.getName());
    }

    @PostMapping("/{achievementId}/notification-read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markNotificationRead(
            @PathVariable Long achievementId,
            Authentication authentication
    ) {
        achievementService.markNotified(authentication.getName(), achievementId);
    }

    @PutMapping("/selection")
    public AchievementVO select(
            @Valid @RequestBody AchievementSelectionDTO request,
            Authentication authentication
    ) {
        return achievementService.select(authentication.getName(), request.achievementId());
    }
}
