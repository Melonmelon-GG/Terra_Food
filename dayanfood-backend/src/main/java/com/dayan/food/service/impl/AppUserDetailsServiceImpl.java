package com.dayan.food.service.impl;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.AppUserDetailsService;
import com.dayan.food.security.AppUserPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsServiceImpl implements AppUserDetailsService {

    private final AppUserMapper appUserMapper;

    public AppUserDetailsServiceImpl(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identity) throws UsernameNotFoundException {
        String normalizedIdentity = com.dayan.food.service.AbuseBudgetService.normalize(identity);
        AppUser appUser = appUserMapper.findByUsernameOrEmail(normalizedIdentity);
        if (appUser == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        return AppUserPrincipal.from(appUser);
    }
}
