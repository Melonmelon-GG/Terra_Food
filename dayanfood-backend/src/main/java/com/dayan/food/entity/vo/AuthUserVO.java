package com.dayan.food.entity.vo;

import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import java.time.LocalDateTime;

public record AuthUserVO(
        Long id,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        UserRole role,
        boolean active,
        LocalDateTime createdAt
) {

    public static AuthUserVO from(AppUser user) {
        return new AuthUserVO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
