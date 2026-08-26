package com.dayan.food.entity.vo;

import java.io.Serializable;

/**
 * 算术人机验证题的面向前端视图。
 */
public record CaptchaVO(
        String captchaId,
        String question
) implements Serializable {
}