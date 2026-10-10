package com.dayan.food.controller;

import com.dayan.food.entity.dto.LoginDTO;
import com.dayan.food.entity.dto.PasswordResetCodeSendDTO;
import com.dayan.food.entity.dto.PasswordResetDTO;
import com.dayan.food.entity.dto.RegistrationCodeSendDTO;
import com.dayan.food.entity.dto.RegisterDTO;
import com.dayan.food.entity.vo.AuthUserVO;
import com.dayan.food.service.AuthService;
import com.dayan.food.service.AbuseBudgetService;
import com.dayan.food.service.PasswordResetCodeService;
import com.dayan.food.service.RegistrationCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import java.util.Map;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RegistrationCodeService registrationCodeService;
    private final PasswordResetCodeService passwordResetCodeService;
    private final AbuseBudgetService abuseBudgetService;
    private final com.dayan.food.security.ClientAddressResolver clientAddresses;

    public AuthController(
            AuthService authService,
            RegistrationCodeService registrationCodeService,
            PasswordResetCodeService passwordResetCodeService,
            AbuseBudgetService abuseBudgetService,
            com.dayan.food.security.ClientAddressResolver clientAddresses
    ) {
        this.authService = authService;
        this.registrationCodeService = registrationCodeService;
        this.passwordResetCodeService = passwordResetCodeService;
        this.abuseBudgetService = abuseBudgetService;
        this.clientAddresses = clientAddresses;
    }

    @PostMapping("/login")
    public AuthUserVO login(@Valid @RequestBody LoginDTO request, HttpServletRequest servletRequest) {
        String budget = abuseBudgetService.login(clientAddresses.resolve(servletRequest), request.username());
        AuthService.LoginResult result;
        try {
            result = authService.login(request);
        } catch (org.springframework.security.core.AuthenticationException invalid) {
            abuseBudgetService.loginFailed(budget);
            throw invalid;
        }
        abuseBudgetService.loginSucceeded(budget);

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

    @PostMapping("/registration-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendRegistrationCode(@Valid @RequestBody RegistrationCodeSendDTO request, HttpServletRequest servletRequest) {
        abuseBudgetService.mail(clientAddresses.resolve(servletRequest), request.email());
        registrationCodeService.sendCode(request.email(), request.captchaId(), request.captchaAnswer());
    }

    @PostMapping("/password-reset-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendPasswordResetCode(@Valid @RequestBody PasswordResetCodeSendDTO request, HttpServletRequest servletRequest) {
        abuseBudgetService.mail(clientAddresses.resolve(servletRequest), request.email());
        passwordResetCodeService.sendCode(request.username(), request.email());
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordResetDTO request) {
        authService.resetPassword(request);
    }

    @GetMapping("/me")
    public AuthUserVO currentUser(Authentication authentication, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return authService.currentUser(authentication.getName());
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
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
