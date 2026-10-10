package com.dayan.food.service.impl;

import com.dayan.food.security.AuthenticatedActor;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.ProfileStats;
import com.dayan.food.entity.vo.ProfileStatsVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.ProfileStatsMapper;
import com.dayan.food.service.ProfileStatsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileStatsServiceImpl implements ProfileStatsService {
    private final AppUserMapper userMapper;
    private final ProfileStatsMapper statsMapper;

    public ProfileStatsServiceImpl(AppUserMapper userMapper, ProfileStatsMapper statsMapper) {
        this.userMapper = userMapper;
        this.statsMapper = statsMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileStatsVO getMine(String username) {
        AppUser user = AuthenticatedActor.resolve(userMapper, username);
        if (user == null || !user.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在或已停用");
        }
        ProfileStats stats = statsMapper.findByUserId(user.getId());
        return new ProfileStatsVO(stats.getViewedFoodCount(), stats.getFavoriteCount());
    }
}
