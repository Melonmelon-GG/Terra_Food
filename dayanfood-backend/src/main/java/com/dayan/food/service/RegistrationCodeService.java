package com.dayan.food.service;

public interface RegistrationCodeService {

    void sendCode(String email);

    String verify(String email, String code);

    void consume(String normalizedEmail);
}
