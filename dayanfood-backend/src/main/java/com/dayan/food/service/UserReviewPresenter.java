package com.dayan.food.service;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.vo.AuthUserVO;
import com.dayan.food.entity.vo.PendingReviewVO;
import com.dayan.food.mapper.UserReviewItemMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户视图组装器：把「已生效字段 + 待审内容」装配成 AuthUserVO。
 * /auth/me（本人）与管理端用户列表共用，避免重复实现。
 */
@Component
public class UserReviewPresenter {

    private final UserReviewItemMapper userReviewItemMapper;

    public UserReviewPresenter(UserReviewItemMapper userReviewItemMapper) {
        this.userReviewItemMapper = userReviewItemMapper;
    }

    public List<PendingReviewVO> pendingOf(Long userId) {
        return userReviewItemMapper.findPendingByUserId(userId).stream()
                .map(PendingReviewVO::from)
                .toList();
    }

    public AuthUserVO toVO(AppUser user) {
        return AuthUserVO.from(user, pendingOf(user.getId()));
    }
}