package com.dayan.food.controller;

import com.dayan.food.entity.vo.ApiErrorVO;
import com.dayan.food.config.RequestIdFilter;
import com.dayan.food.service.RegistrationCodeDeliveryException;
import com.dayan.food.service.UploadTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorVO handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return error("AUTHENTICATION_FAILED", exception.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorVO handleConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        // 审计项 5.8：唯一键冲突等数据库约束违规返回 409 而非 500，便于前端区分“数据已存在”。
        return error("DATA_CONFLICT", "数据冲突：记录已存在或违反唯一约束", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiErrorVO handlePayloadTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return error("UPLOAD_TOO_LARGE", "上传文件超出大小限制", request);
    }

    @ExceptionHandler(UploadTooLargeException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiErrorVO handlePayloadTooLarge(UploadTooLargeException exception, HttpServletRequest request) {
        return error("UPLOAD_TOO_LARGE", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorVO handleBadRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数校验失败");
        return error("VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorVO handleInvalidRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return error("INVALID_REQUEST", exception.getMessage(), request);
    }

    @ExceptionHandler(RegistrationCodeDeliveryException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorVO handleMailDelivery(RegistrationCodeDeliveryException exception, HttpServletRequest request) {
        return error("MAIL_UNAVAILABLE", exception.getMessage(), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorVO handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return error("MALFORMED_REQUEST", "请求格式或参数类型无效", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiErrorVO handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return error("METHOD_NOT_ALLOWED", "请求方法不受支持", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiErrorVO handleUnsupportedMedia(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return error("UNSUPPORTED_MEDIA_TYPE", "请求内容类型不受支持", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorVO> handleResponseStatus(ResponseStatusException exception,
                                                            HttpServletRequest request) {
        int status = exception.getStatusCode().value();
        String code = switch (status) {
            case 401 -> "AUTHENTICATION_REQUIRED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 409 -> "REQUEST_CONFLICT";
            case 429 -> "RATE_LIMITED";
            default -> "REQUEST_FAILED";
        };
        String message = exception.getReason() == null ? "请求处理失败" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).headers(exception.getHeaders()).body(error(code, message, request));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorVO handleUnexpected(Exception exception, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("Unhandled API exception, requestId={}", requestId, exception);
        return new ApiErrorVO("INTERNAL_ERROR", "服务暂时不可用，请稍后重试", requestId);
    }

    private ApiErrorVO error(String code, String message, HttpServletRequest request) {
        return new ApiErrorVO(code, message, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        if (requestId != null) return requestId.toString();
        // Multipart parsing can fail before the normal request filter chain has
        // attached an identifier. The error body must still carry a correlation
        // value so an upload failure is diagnosable.
        String generated = java.util.UUID.randomUUID().toString();
        request.setAttribute(RequestIdFilter.ATTRIBUTE, generated);
        return generated;
    }
}
