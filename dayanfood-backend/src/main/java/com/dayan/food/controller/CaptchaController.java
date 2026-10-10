package com.dayan.food.controller;

import com.dayan.food.entity.vo.CaptchaVO;
import com.dayan.food.service.CaptchaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class CaptchaController {

    private final CaptchaService captchaService;
    private final com.dayan.food.service.AbuseBudgetService budgets;
    private final com.dayan.food.security.ClientAddressResolver addresses;

    public CaptchaController(CaptchaService captchaService, com.dayan.food.service.AbuseBudgetService budgets,
            com.dayan.food.security.ClientAddressResolver addresses) {
        this.captchaService = captchaService;
        this.budgets = budgets;
        this.addresses = addresses;
    }

    @GetMapping("/captcha")
    public CaptchaVO captcha(jakarta.servlet.http.HttpServletRequest request) {
        String address = addresses.resolve(request);
        var session = request.getSession(false);
        budgets.captcha(address, session == null ? "anonymous:" + address : session.getId());
        return captchaService.issue();
    }
}
