package com.dayan.food.service.impl;

import com.dayan.food.entity.po.Achievement;
import com.dayan.food.entity.vo.AchievementVO;
import com.dayan.food.mapper.AchievementMapper;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.AchievementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AchievementServiceImpl implements AchievementService {

    private static final String FIRST_LOGIN_CODE = "FIRST_LOGIN";

    private final AchievementMapper achievementMapper;
    private final AppUserMapper appUserMapper;

    public AchievementServiceImpl(AchievementMapper achievementMapper, AppUserMapper appUserMapper) {
        this.achievementMapper = achievementMapper;
        this.appUserMapper = appUserMapper;
    }

    @Override
    @Transactional
    public void awardFirstLogin(String username) {
        var user = appUserMapper.findByUsername(username);
        Achievement achievement = achievementMapper.findByCode(FIRST_LOGIN_CODE);
        if (user == null || achievement == null) {
            throw new IllegalStateException("首次登录成就配置不存在");
        }
        // 数据库主键保证重复登录不会重复解锁同一成就。
        achievementMapper.unlock(user.getId(), achievement.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchievementVO> listUnlocked(String username) {
        return achievementMapper.findUnlockedByUsername(username).stream()
                .map(AchievementVO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchievementVO> listUnnotified(String username) {
        return achievementMapper.findUnnotifiedByUsername(username).stream()
                .map(AchievementVO::from)
                .toList();
    }

    @Override
    @Transactional
    public void markNotified(String username, Long achievementId) {
        achievementMapper.markNotified(username, achievementId);
    }

    @Override
    @Transactional
    public AchievementVO select(String username, Long achievementId) {
        achievementMapper.clearSelection(username);
        if (achievementMapper.select(username, achievementId) != 1) {
            throw new IllegalArgumentException("只能选择已经获得的蚀刻章");
        }
        return achievementMapper.findUnlockedByUsername(username).stream()
                .filter(achievement -> achievement.getId().equals(achievementId))
                .findFirst()
                .map(AchievementVO::from)
                .orElseThrow(() -> new IllegalStateException("蚀刻章选择结果读取失败"));
    }
}
