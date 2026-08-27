package com.dayan.food.service.impl;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.FoodLike;
import com.dayan.food.entity.vo.FoodLikeStatusVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodLikeMapper;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.service.FoodLikeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FoodLikeServiceImpl implements FoodLikeService {

    private final FoodLikeMapper foodLikeMapper;
    private final FoodMapper foodMapper;
    private final AppUserMapper appUserMapper;

    public FoodLikeServiceImpl(
            FoodLikeMapper foodLikeMapper,
            FoodMapper foodMapper,
            AppUserMapper appUserMapper
    ) {
        this.foodLikeMapper = foodLikeMapper;
        this.foodMapper = foodMapper;
        this.appUserMapper = appUserMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public FoodLikeStatusVO status(Long foodId, String username) {
        requireApprovedFood(foodId);
        return readStatus(foodId, username);
    }

    @Override
    @Transactional
    public FoodLikeStatusVO like(Long foodId, String username) {
        requireApprovedFood(foodId);
        AppUser user = requireActiveUser(username);
        // INSERT IGNORE + 复合主键幂等：重复点赞不报错也不重复计数
        foodLikeMapper.insertIgnore(new FoodLike(foodId, user.getId()));
        return readStatus(foodId, username);
    }

    @Override
    @Transactional
    public FoodLikeStatusVO unlike(Long foodId, String username) {
        requireApprovedFood(foodId);
        AppUser user = requireActiveUser(username);
        foodLikeMapper.deleteIgnore(foodId, user.getId());
        return readStatus(foodId, username);
    }

    private FoodLikeStatusVO readStatus(Long foodId, String username) {
        boolean likedByMe = false;
        if (username != null) {
            AppUser user = appUserMapper.findByUsername(username);
            likedByMe = user != null && user.isActive()
                    && foodLikeMapper.exists(foodId, user.getId()) > 0;
        }
        return new FoodLikeStatusVO(foodLikeMapper.countByFoodId(foodId), likedByMe);
    }

    private void requireApprovedFood(Long foodId) {
        if (foodMapper.findById(foodId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "美食不存在");
        }
    }

    private AppUser requireActiveUser(String username) {
        AppUser user = appUserMapper.findByUsername(username);
        if (user == null || !user.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在或已停用");
        }
        return user;
    }
}