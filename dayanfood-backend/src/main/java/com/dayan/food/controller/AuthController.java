package com.dayan.food.controller;

import com.dayan.food.entity.dto.LoginDTO;
import com.dayan.food.entity.dto.RegisterDTO;
import com.dayan.food.entity.vo.AuthUserVO;
import com.dayan.food.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthUserVO login(@Valid @RequestBody LoginDTO request, HttpServletRequest servletRequest) {
        var result = authService.login(request);

        // 认证成功后更换 Session，防止复用登录前的会话标识造成会话固定风险。
        var existingSession = servletRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(result.authentication());
        SecurityContextHolder.setContext(context);
        servletRequest.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );

        return result.user();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUserVO register(@Valid @RequestBody RegisterDTO request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    public AuthUserVO currentUser(Authentication authentication) {
        return authService.currentUser(authentication.getName());
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
