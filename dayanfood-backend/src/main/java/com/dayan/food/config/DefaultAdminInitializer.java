package com.dayan.food.config;

import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.service.AppUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.initial-admin", name = "enabled", havingValue = "true")
public class DefaultAdminInitializer implements ApplicationRunner {

    private final AppUserService appUserService;
    private final String adminPassword;

    public DefaultAdminInitializer(
            AppUserService appUserService,
            @Value("${app.initial-admin.password:}") String adminPassword
    ) {
        this.appUserService = appUserService;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword.isBlank()) {
            throw new IllegalStateException("启用初始管理员时必须配置 INITIAL_ADMIN_PASSWORD");
        }
        appUserService.createIfMissing("admin", adminPassword, "珍馐志管理员", UserRole.ADMIN);
    }
}
