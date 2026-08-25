package com.dayan.food.entity.vo;

import com.dayan.food.entity.po.FoodComment;

import java.time.LocalDateTime;

public record FoodCommentVO(
        Long id,
        Long foodId,
        UserSummaryVO author,
        String content,
        LocalDateTime createdAt
) {

    public static FoodCommentVO from(FoodComment comment) {
        return new FoodCommentVO(
                comment.getId(),
                comment.getFoodId(),
                UserSummaryVO.from(comment.getAuthor()),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
