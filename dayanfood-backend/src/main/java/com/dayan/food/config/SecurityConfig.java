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
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/internal/agent/**"))
                .cors(Customizer.withDefaults())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.setHeader("Cache-Control", "private, no-store");
                            response.getWriter().write("{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"请先登录\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            String code = exception instanceof org.springframework.security.web.csrf.CsrfException
                                    ? "CSRF_INVALID" : "ACCESS_DENIED";
                            response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"请求校验失败或没有操作权限\"}");
                        }))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/captcha",
                                "/api/auth/registration-code",
                                "/api/auth/password-reset-code",
                                "/api/auth/password-reset",
                                "/api/auth/csrf",
                                "/api/internal/agent/**",
                                "/uploads/**",
                                "/error"
                        )
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/foods/mine/page").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/foods/**", "/api/regions/**", "/api/users/**", "/api/food-tags/**").permitAll()
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
