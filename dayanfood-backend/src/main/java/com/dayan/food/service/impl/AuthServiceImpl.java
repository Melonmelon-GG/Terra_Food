package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.LoginDTO;
import com.dayan.food.entity.dto.RegisterDTO;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.vo.AuthUserVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            AppUserMapper appUserMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.authenticationManager = authenticationManager;
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResult login(LoginDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );

        String expectedAuthority = "ROLE_" + request.role().name();
        boolean roleMatches = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(expectedAuthority));
        if (!roleMatches) {
            throw new BadCredentialsException("登录类型与账号角色不匹配");
        }

        return new LoginResult(authentication, findUser(authentication.getName()));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthUserVO currentUser(String username) {
        return findUser(username);
    }

    @Override
    @Transactional
    public AuthUserVO register(RegisterDTO request) {
        if (appUserMapper.countByUsername(request.username()) > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        // 公开注册永远只创建普通用户，管理员权限不能通过自助注册获得。
        var user = new AppUser(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                UserRole.USER
        );
        appUserMapper.insert(user);
        return AuthUserVO.from(user);
    }

    private AuthUserVO findUser(String username) {
        AppUser user = appUserMapper.findByUsername(username);
        if (user == null) {
            throw new BadCredentialsException("用户不存在");
        }
        return AuthUserVO.from(user);
    }
}
