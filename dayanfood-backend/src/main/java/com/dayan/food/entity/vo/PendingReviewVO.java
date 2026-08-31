package com.dayan.food.entity.vo;

import com.dayan.food.entity.enums.ReviewField;
import com.dayan.food.entity.po.UserReviewItem;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单条待审内容（该记录本身处于 PENDING）。供本人 /auth/me 与管理端用户列表共同展示。
 */
public record PendingReviewVO(
        Long id,
        ReviewField field,
        String currentValue,
        String pendingValue,
        LocalDateTime requestedAt
) implements Serializable {

    public static PendingReviewVO from(UserReviewItem item) {
        return new PendingReviewVO(
                item.getId(),
                item.getField(),
                item.getCurrentValue(),
                item.getPendingValue(),
                item.getRequestedAt()
        );
    }
}