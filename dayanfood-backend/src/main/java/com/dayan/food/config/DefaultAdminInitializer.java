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
    private final String adminUsername;
    private final String adminPassword;
    private final String adminDisplayName;

    public DefaultAdminInitializer(
            AppUserService appUserService,
            @Value("${app.initial-admin.username:admin}") String adminUsername,
            @Value("${app.initial-admin.password:}") String adminPassword,
            @Value("${app.initial-admin.display-name:珍馐志管理员}") String adminDisplayName
    ) {
        this.appUserService = appUserService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminDisplayName = adminDisplayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword.isBlank()) {
            throw new IllegalStateException("启用初始管理员时必须配置 INITIAL_ADMIN_PASSWORD");
        }
        if (!adminUsername.matches("[A-Za-z0-9_]{3,50}")) {
            throw new IllegalStateException("INITIAL_ADMIN_USERNAME 必须为 3-50 位字母、数字或下划线");
        }
        if (adminDisplayName.isBlank() || adminDisplayName.length() > 50) {
            throw new IllegalStateException("INITIAL_ADMIN_DISPLAY_NAME 必须为 1-50 个字符");
        }
        appUserService.createIfMissing(
                adminUsername,
                adminPassword,
                adminDisplayName.trim(),
                UserRole.ADMIN
        );
    }
}
