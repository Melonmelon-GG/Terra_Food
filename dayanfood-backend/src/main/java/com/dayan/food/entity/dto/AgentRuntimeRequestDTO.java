package com.dayan.food.entity.dto;

import java.util.List;

public record AgentRuntimeRequestDTO(
        String subjectId,
        String serviceContext,
        String username,
        String displayName,
        String message,
        List<String> availableTracks,
        Long currentFoodId,
        String currentFoodName
) {
}
