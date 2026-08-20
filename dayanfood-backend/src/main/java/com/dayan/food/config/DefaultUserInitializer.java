package com.dayan.food.config;

import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.service.AppUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.initial-users", name = "enabled", havingValue = "true")
public class DefaultUserInitializer implements ApplicationRunner {

    private final AppUserService appUserService;
    private final String adminPassword;
    private final String userPassword;

    public DefaultUserInitializer(
            AppUserService appUserService,
            @Value("${app.initial-users.admin-password:}") String adminPassword,
            @Value("${app.initial-users.user-password:}") String userPassword
    ) {
        this.appUserService = appUserService;
        this.adminPassword = adminPassword;
        this.userPassword = userPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword.isBlank() || userPassword.isBlank()) {
            throw new IllegalStateException(
                    "启用初始用户时必须配置 INITIAL_ADMIN_PASSWORD 和 INITIAL_USER_PASSWORD"
            );
        }
        appUserService.createIfMissing("admin", adminPassword, "珍馐志管理员", UserRole.ADMIN);
        appUserService.createIfMissing("user", userPassword, "寻味人", UserRole.USER);
    }
}
