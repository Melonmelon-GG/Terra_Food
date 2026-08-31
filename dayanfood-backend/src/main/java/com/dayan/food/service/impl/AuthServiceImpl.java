package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.LoginDTO;
import com.dayan.food.entity.dto.PasswordResetDTO;
import com.dayan.food.entity.dto.RegisterDTO;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.vo.AuthUserVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.AchievementService;
import com.dayan.food.service.AuthService;
import com.dayan.food.service.PasswordResetCodeService;
import com.dayan.food.service.RegistrationCodeService;
import com.dayan.food.service.UserReviewPresenter;
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
    private final PasswordResetCodeService passwordResetCodeService;
    private final AchievementService achievementService;
    private final UserReviewPresenter reviewPresenter;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            AppUserMapper appUserMapper,
            PasswordEncoder passwordEncoder,
            RegistrationCodeService registrationCodeService,
            PasswordResetCodeService passwordResetCodeService,
            AchievementService achievementService,
            UserReviewPresenter reviewPresenter
    ) {
        this.authenticationManager = authenticationManager;
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.registrationCodeService = registrationCodeService;
        this.passwordResetCodeService = passwordResetCodeService;
        this.achievementService = achievementService;
        this.reviewPresenter = reviewPresenter;
    }

    @Override
    @Transactional
    public LoginResult login(LoginDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );

        // 登录渠道只决定默认入口，不应成为账号类型的硬门禁：管理员/子管理员同样可以从
        // 用户渠道登录（浏览、收录等前台行为）；管理员渠道仍要求管理员权限，防止普通用户借壳登录。
        boolean roleMatches = request.role() == UserRole.ADMIN
                ? hasAnyAuthority(authentication, "ROLE_ADMIN", "ROLE_SUB_ADMIN")
                : hasAnyAuthority(authentication, "ROLE_USER", "ROLE_ADMIN", "ROLE_SUB_ADMIN");
        if (!roleMatches) {
            throw new BadCredentialsException("登录类型与账号角色不匹配");
        }

        achievementService.awardFirstLogin(authentication.getName());
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
        // 前置 count 校验已给出友好提示；并发下的唯一键竞态直接向上抛出，
        // 由全局 409 处理器转为 CONFLICT，不再吞成 400。
        appUserMapper.insert(user);
        registrationCodeService.consume(normalizedEmail);
        return AuthUserVO.from(user);
    }

    @Override
    @Transactional
    public void resetPassword(PasswordResetDTO request) {
        String normalizedEmail = passwordResetCodeService.verify(
                request.username(),
                request.email(),
                request.verificationCode()
        );
        int updated = appUserMapper.updatePassword(
                request.username().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.newPassword())
        );
        if (updated != 1) {
            throw new IllegalArgumentException("用户名与邮箱不匹配，或账号不可用");
        }
        passwordResetCodeService.consume(request.username(), normalizedEmail);
    }

    private AuthUserVO findUser(String username) {
        AppUser user = appUserMapper.findByUsername(username);
        if (user == null) {
            throw new BadCredentialsException("用户不存在");
        }
        return reviewPresenter.toVO(user);
    }

    private boolean hasAnyAuthority(Authentication authentication, String... expectedAuthorities) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> java.util.Arrays.asList(expectedAuthorities)
                        .contains(authority.getAuthority()));
    }
}
