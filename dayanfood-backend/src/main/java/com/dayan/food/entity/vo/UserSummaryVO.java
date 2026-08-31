package com.dayan.food.entity.vo;

import com.dayan.food.entity.po.AppUser;

import java.io.Serializable;

/**
 * 可公开展示的用户信息，不包含邮箱、角色和账号状态等敏感字段。
 */
public record UserSummaryVO(
        Long id,
        String username,
        String displayName,
        String avatarUrl
) implements Serializable {

    public static UserSummaryVO from(AppUser user) {
        return new UserSummaryVO(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
    }

    public static UserSummaryVO fallback(String username) {
        return new UserSummaryVO(null, username, username, null);
    }
}
