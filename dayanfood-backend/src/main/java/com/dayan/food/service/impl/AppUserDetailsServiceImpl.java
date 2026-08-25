package com.dayan.food.service.impl;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.AppUserDetailsService;
import org.springframework.security.core.userdetails.User;
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
        String normalizedIdentity = identity == null ? "" : identity.trim();
        AppUser appUser = appUserMapper.findByUsernameOrEmail(normalizedIdentity);
        if (appUser == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        return User.withUsername(appUser.getUsername())
                .password(appUser.getPassword())
                .roles(appUser.getRole().name())
                .disabled(!appUser.isActive())
                .build();
    }
}
