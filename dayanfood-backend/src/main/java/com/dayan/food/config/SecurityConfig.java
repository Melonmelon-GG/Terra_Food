package com.dayan.food.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ActiveSessionFilter activeSessionFilter
    ) throws Exception {
        return http
                // 当前前后端通过同源 Vite 代理访问；API 使用 Session，但不依赖表单 CSRF token。
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/captcha",
                                "/api/auth/registration-code",
                                "/api/auth/password-reset-code",
                                "/api/auth/password-reset",
                                "/uploads/**",
                                "/error"
                        )
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/foods/**", "/api/regions/**").permitAll()
                        // 游客地图选点同样依赖地区白名单解析（只读查询，无写副作用）。
                        .requestMatchers(HttpMethod.POST, "/api/regions/resolve").permitAll()
                        // 角色授予只能由主管理员执行，必须放在后台通配规则之前。
                        .requestMatchers(HttpMethod.PATCH, "/api/admin/users/*/role").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/foods/**")
                        .hasAnyRole("ADMIN", "SUB_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/foods/import")
                        .hasAnyRole("ADMIN", "SUB_ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUB_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/foods/**", "/api/images/**")
                        .hasAnyRole("USER", "ADMIN", "SUB_ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                // 基础安全响应头：禁 MIME 嗅探、同源嵌入、来源策略收紧。
                // CSP 涉及 Vue 内联样式风险，暂缓；后续如启用需评估 style-src。
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.sameOrigin())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                        ))
                )
                .addFilterBefore(activeSessionFilter, AuthorizationFilter.class)
                .build();
    }
}
