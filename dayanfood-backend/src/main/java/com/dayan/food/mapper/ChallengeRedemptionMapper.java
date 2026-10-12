package com.dayan.food.mapper;

import org.apache.ibatis.annotations.Param;

public interface ChallengeRedemptionMapper {
    int insert(@Param("generation") String generation, @Param("purpose") String purpose, @Param("identityDigest") String identityDigest);
}
