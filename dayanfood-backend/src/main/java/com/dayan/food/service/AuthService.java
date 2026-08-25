package com.dayan.food.service;

import com.dayan.food.entity.dto.LoginDTO;
import com.dayan.food.entity.dto.PasswordResetDTO;
import com.dayan.food.entity.dto.RegisterDTO;
import com.dayan.food.entity.vo.AuthUserVO;
import org.springframework.security.core.Authentication;

public interface AuthService {

    LoginResult login(LoginDTO request);

    AuthUserVO currentUser(String username);

    AuthUserVO register(RegisterDTO request);

    void resetPassword(PasswordResetDTO request);

    record LoginResult(Authentication authentication, AuthUserVO user) {
    }
}
