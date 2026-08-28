package com.dayan.food.entity.vo;

import java.util.List;

public record AgentChatVO(
        String reply,
        AgentClientActionVO clientAction,
        List<AgentFoodRecommendationVO> recommendations,
        AgentCommentDraftVO commentDraft
) {
}
