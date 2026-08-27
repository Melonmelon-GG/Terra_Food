package com.dayan.food.service;

import com.dayan.food.entity.vo.CaptchaVO;

public interface CaptchaService {

    /**
     * 签发一道算术人机验证题，答案摘要存入 Redis，限时且一次性使用。
     */
    CaptchaVO issue();

    /**
     * 校验答题结果，成功即消费该验证码（一次性），失败累计错误次数。
     */
    void verify(String captchaId, String answer);
}