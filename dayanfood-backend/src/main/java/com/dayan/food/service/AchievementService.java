package com.dayan.food.service;

import com.dayan.food.entity.vo.AchievementVO;

import java.util.List;

public interface AchievementService {

    void awardFirstLogin(String username);

    List<AchievementVO> listUnlocked(String username);

    List<AchievementVO> listUnnotified(String username);

    void markNotified(String username, Long achievementId);

    AchievementVO select(String username, Long achievementId);
}
