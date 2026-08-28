package com.dayan.food.entity.vo;

import java.util.List;

public record FoodImportResultVO(
        int totalRows,
        int importedCount,
        int skippedCount,
        int duplicateCount,
        int anonymousCount,
        int invalidCount,
        int truncatedCount,
        List<FoodImportIssueVO> issues
) {
}
