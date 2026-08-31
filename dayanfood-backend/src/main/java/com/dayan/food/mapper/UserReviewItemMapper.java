package com.dayan.food.mapper;

import com.dayan.food.entity.enums.ReviewField;
import com.dayan.food.entity.enums.ReviewStatus;
import com.dayan.food.entity.po.UserReviewItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserReviewItemMapper {

    int upsertPending(
            @Param("userId") Long userId,
            @Param("field") ReviewField field,
            @Param("currentValue") String currentValue,
            @Param("pendingValue") String pendingValue
    );

    List<UserReviewItem> findPendingByUserId(@Param("userId") Long userId);

    UserReviewItem findPendingByUserAndField(@Param("userId") Long userId, @Param("field") ReviewField field);

    List<UserReviewItem> findPendingByIds(@Param("userIds") List<Long> userIds);

    UserReviewItem findPendingById(@Param("id") Long id);

    int approveItem(@Param("id") Long id, @Param("reviewedBy") String reviewedBy);

    int rejectItem(@Param("id") Long id, @Param("reviewedBy") String reviewedBy);

    List<UserReviewItem> findPendingByField(@Param("field") ReviewField field, @Param("limit") int limit);
}