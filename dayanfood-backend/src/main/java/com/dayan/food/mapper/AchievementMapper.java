package com.dayan.food.mapper;

import com.dayan.food.entity.po.Achievement;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AchievementMapper {

    Achievement findByCode(String code);

    int unlock(@Param("userId") Long userId, @Param("achievementId") Long achievementId);

    List<Achievement> findUnlockedByUsername(String username);

    List<Achievement> findUnnotifiedByUsername(String username);

    int markNotified(@Param("username") String username, @Param("achievementId") Long achievementId);

    int clearSelection(String username);

    int select(@Param("username") String username, @Param("achievementId") Long achievementId);
}
