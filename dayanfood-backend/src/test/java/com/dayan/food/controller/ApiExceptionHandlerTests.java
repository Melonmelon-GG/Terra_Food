package com.dayan.food.controller;

import com.dayan.food.entity.vo.ApiErrorVO;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 审计项 5.8：数据库约束违规映射 409，超大上传映射 413（而非 500）。
 */
class ApiExceptionHandlerTests {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void dataIntegrityConflictMapsTo409() throws NoSuchMethodException {
        ResponseStatus status = ApiExceptionHandler.class
                .getMethod("handleConflict", DataIntegrityViolationException.class)
                .getAnnotation(ResponseStatus.class);

        assertEquals(HttpStatus.CONFLICT, status.value());

        ApiErrorVO body = handler.handleConflict(new DataIntegrityViolationException("duplicate key"));
        assertNotNull(body.message());
    }

    @Test
    void oversizedUploadMapsTo413() throws NoSuchMethodException {
        ResponseStatus status = ApiExceptionHandler.class
                .getMethod("handlePayloadTooLarge", MaxUploadSizeExceededException.class)
                .getAnnotation(ResponseStatus.class);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, status.value());

        ApiErrorVO body = handler.handlePayloadTooLarge(new MaxUploadSizeExceededException(1024L));
        assertNotNull(body.message());
    }
}
