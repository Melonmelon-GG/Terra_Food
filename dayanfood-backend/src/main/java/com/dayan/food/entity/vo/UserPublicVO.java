package com.dayan.food.entity.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 公开用户主页视图：仅暴露可公开展示的用户信息与该用户已通过审核的菜品，
 * 不包含邮箱、角色、账号状态与任何私密字段。
 */
public record UserPublicVO(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        String signature,
        AchievementVO selectedAchievement,
        EtchingDesignVO selectedEtching,
        List<FoodVO> foods
) implements Serializable {
}
