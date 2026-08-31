package com.dayan.food.entity.vo;

import com.dayan.food.entity.enums.SignatureStatus;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import java.time.LocalDateTime;
import java.util.List;

public record AuthUserVO(
        Long id,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        String signature,
        String signaturePending,
        SignatureStatus signatureStatus,
        UserRole role,
        boolean active,
        LocalDateTime createdAt,
        List<PendingReviewVO> pendingReviews
) {

    public static AuthUserVO from(AppUser user) {
        return new AuthUserVO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getSignature(),
                user.getSignaturePending(),
                user.getSignatureStatus(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                List.of()
        );
    }

    public static AuthUserVO from(AppUser user, List<PendingReviewVO> pendingReviews) {
        return new AuthUserVO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getSignature(),
                user.getSignaturePending(),
                user.getSignatureStatus(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                pendingReviews
        );
    }
}