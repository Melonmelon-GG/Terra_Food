package com.dayan.food.controller;

import com.dayan.food.entity.vo.ApiErrorVO;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorVO handleAuthentication(AuthenticationException exception) {
        return new ApiErrorVO(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorVO handleBadRequest(IllegalArgumentException exception) {
        return new ApiErrorVO(exception.getMessage());
    }
}
