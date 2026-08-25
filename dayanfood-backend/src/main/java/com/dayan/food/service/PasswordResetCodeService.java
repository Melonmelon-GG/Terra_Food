package com.dayan.food.service;

public interface PasswordResetCodeService {

    void sendCode(String username, String email);

    String verify(String username, String email, String code);

    void consume(String username, String normalizedEmail);
}
