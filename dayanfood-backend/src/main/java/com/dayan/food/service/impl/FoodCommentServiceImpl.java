package com.dayan.food.service.impl;

import com.dayan.food.security.AuthenticatedActor;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.FoodComment;
import com.dayan.food.entity.vo.FoodCommentVO;
import com.dayan.food.entity.vo.FoodCommentPageVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodCommentMapper;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.service.FoodCommentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FoodCommentServiceImpl implements FoodCommentService {

    private final FoodCommentMapper foodCommentMapper;
    private final FoodMapper foodMapper;
    private final AppUserMapper appUserMapper;

    public FoodCommentServiceImpl(
            FoodCommentMapper foodCommentMapper,
            FoodMapper foodMapper,
            AppUserMapper appUserMapper
    ) {
        this.foodCommentMapper = foodCommentMapper;
        this.foodMapper = foodMapper;
        this.appUserMapper = appUserMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodCommentVO> list(Long foodId) {
        requireApprovedFood(foodId);
        return foodCommentMapper.findByFoodId(foodId).stream()
                .map(FoodCommentVO::from)
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public FoodCommentPageVO page(Long foodId, int page, int pageSize) {
        requireApprovedFood(foodId);
        int size=Math.min(Math.max(pageSize,1),50); int total=foodCommentMapper.countByFoodId(foodId);
        int pages=Math.max(1,(int)Math.ceil((double)total/size)); int normalized=Math.min(Math.max(page,1),pages);
        var items=foodCommentMapper.findPageByFoodId(foodId,(normalized-1)*size,size).stream().map(FoodCommentVO::from).toList();
        return new FoodCommentPageVO(items,total,normalized,size);
    }

    @Override
    @Transactional
    public FoodCommentVO create(Long foodId, String content, String username) {
        requireApprovedFood(foodId);
        AppUser author = AuthenticatedActor.resolve(appUserMapper, username);
        if (author == null || !author.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在或已停用");
        }

        String normalizedContent = content.trim();
        if (normalizedContent.isEmpty()) {
            throw new IllegalArgumentException("评论内容不能为空");
        }

        var comment = new FoodComment(foodId, author, normalizedContent);
        if (foodCommentMapper.insert(comment) != 1) {
            throw new IllegalStateException("评论保存失败");
        }
        return FoodCommentVO.from(comment);
    }

    private void requireApprovedFood(Long foodId) {
        if (foodMapper.findById(foodId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "美食不存在");
        }
    }
}
