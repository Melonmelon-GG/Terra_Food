package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.LoginDTO;
import com.dayan.food.entity.dto.RegisterDTO;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.vo.AuthUserVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.AuthService;
import com.dayan.food.service.RegistrationCodeService;
import org.springframework.dao.DuplicateKeyException;
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
    private final RegistrationCodeService registrationCodeService;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            AppUserMapper appUserMapper,
            PasswordEncoder passwordEncoder,
            RegistrationCodeService registrationCodeService
    ) {
        this.authenticationManager = authenticationManager;
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.registrationCodeService = registrationCodeService;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResult login(LoginDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );

        boolean roleMatches = request.role() == UserRole.ADMIN
                ? hasAnyAuthority(authentication, "ROLE_ADMIN", "ROLE_SUB_ADMIN")
                : request.role() == UserRole.USER && hasAnyAuthority(authentication, "ROLE_USER");
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
        String normalizedEmail = registrationCodeService.verify(request.email(), request.verificationCode());
        if (appUserMapper.countByUsername(request.username()) > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (appUserMapper.countByEmail(normalizedEmail) > 0) {
            throw new IllegalArgumentException("该邮箱已注册");
        }

        // 公开注册永远只创建普通用户，管理员权限不能通过自助注册获得。
        var user = new AppUser(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                normalizedEmail,
                UserRole.USER
        );
        try {
            appUserMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名或邮箱已存在", exception);
        }
        registrationCodeService.consume(normalizedEmail);
        return AuthUserVO.from(user);
    }

    private AuthUserVO findUser(String username) {
        AppUser user = appUserMapper.findByUsername(username);
        if (user == null) {
            throw new BadCredentialsException("用户不存在");
        }
        return AuthUserVO.from(user);
    }

    private boolean hasAnyAuthority(Authentication authentication, String... expectedAuthorities) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> java.util.Arrays.asList(expectedAuthorities)
                        .contains(authority.getAuthority()));
    }
}
