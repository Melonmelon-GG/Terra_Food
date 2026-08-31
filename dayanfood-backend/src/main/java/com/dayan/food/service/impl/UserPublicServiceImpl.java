package com.dayan.food.service.impl;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.UserPublicVO;
import com.dayan.food.entity.vo.AchievementVO;
import com.dayan.food.mapper.AchievementMapper;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.service.UserPublicService;
import com.dayan.food.service.EtchingDesignService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserPublicServiceImpl implements UserPublicService {

    private static final int MAX_PUBLIC_FOODS = 100;

    private final AppUserMapper appUserMapper;
    private final FoodMapper foodMapper;
    private final AchievementMapper achievementMapper;
    private final EtchingDesignService etchingDesignService;

    public UserPublicServiceImpl(AppUserMapper appUserMapper, FoodMapper foodMapper,
                                 AchievementMapper achievementMapper, EtchingDesignService etchingDesignService) {
        this.appUserMapper = appUserMapper;
        this.foodMapper = foodMapper;
        this.achievementMapper = achievementMapper;
        this.etchingDesignService = etchingDesignService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserPublicVO getProfile(Long userId) {
        // 公开摘要查询不加载 password 列，避免凭据哈希进入公开读路径的内存。
        AppUser user = appUserMapper.findPublicById(userId);
        // 已停用或不存在用户不出现在公开面。
        if (user == null || !user.isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }

        List<FoodVO> foods = foodMapper.findApprovedByCreatedBy(user.getUsername(), MAX_PUBLIC_FOODS).stream()
                .map(food -> FoodVO.from(food, user))
                .toList();

        String signature = user.getSignature() == null || user.getSignature().isBlank()
                ? null
                : user.getSignature().trim();
        AchievementVO selectedAchievement = achievementMapper.findUnlockedByUsername(user.getUsername()).stream()
                .filter(achievement -> achievement.isSelected())
                .findFirst().map(AchievementVO::from).orElse(null);
        return new UserPublicVO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                signature,
                selectedAchievement,
                etchingDesignService.getSelected(user.getId()),
                foods
        );
    }
}
