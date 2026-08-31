package com.dayan.food.controller;

import com.dayan.food.entity.vo.ApiErrorVO;
import com.dayan.food.service.RegistrationCodeDeliveryException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorVO handleAuthentication(AuthenticationException exception) {
        return new ApiErrorVO(exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorVO handleConflict(DataIntegrityViolationException exception) {
        // 审计项 5.8：唯一键冲突等数据库约束违规返回 409 而非 500，便于前端区分“数据已存在”。
        return new ApiErrorVO("数据冲突：记录已存在或违反唯一约束");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiErrorVO handlePayloadTooLarge(MaxUploadSizeExceededException exception) {
        return new ApiErrorVO("上传文件超出大小限制");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorVO handleBadRequest(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数校验失败");
        return new ApiErrorVO(message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorVO handleInvalidRequest(IllegalArgumentException exception) {
        return new ApiErrorVO(exception.getMessage());
    }

    @ExceptionHandler(RegistrationCodeDeliveryException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorVO handleMailDelivery(RegistrationCodeDeliveryException exception) {
        return new ApiErrorVO(exception.getMessage());
    }
}
