package com.dayan.food.config;

import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.security.AppUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ActiveSessionFilter extends OncePerRequestFilter {

    private final AppUserMapper appUserMapper;

    public ActiveSessionFilter(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            if (!(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
                reject(request, response);
                return;
            }
            var user = appUserMapper.findById(principal.userId());
            if (user == null || !user.isActive()
                    || !user.getSubjectId().equals(principal.subjectId())
                    || user.getAuthVersion() != principal.authVersion()) {
                reject(request, response);
                return;
            }

            String expectedUserId = request.getHeader("X-Expected-User-Id");
            if (!java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
                    && expectedUserId != null && !principal.userId().toString().equals(expectedUserId)) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"IDENTITY_CHANGED\",\"message\":\"登录账号已变化，请刷新后重试\"}");
                return;
            }

            String expectedAuthority = "ROLE_" + user.getRole().name();
            boolean roleIsCurrent = authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(expectedAuthority));
            if (!roleIsCurrent) {
                // 角色升降级后立即刷新当前会话权限，无需等待用户重新登录。
                var refreshed = UsernamePasswordAuthenticationToken.authenticated(
                        authentication.getPrincipal(),
                        authentication.getCredentials(),
                        List.of(new SimpleGrantedAuthority(expectedAuthority))
                );
                refreshed.setDetails(authentication.getDetails());
                SecurityContextHolder.getContext().setAuthentication(refreshed);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"SESSION_REVOKED\",\"message\":\"会话已失效，请重新登录\"}");
    }
}
