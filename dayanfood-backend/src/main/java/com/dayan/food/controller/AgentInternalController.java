package com.dayan.food.controller;

import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.service.FoodService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/api/internal/agent")
public class AgentInternalController {

    private final FoodService foodService;
    private final String internalToken;
    private final com.dayan.food.security.AgentServiceContext contexts;

    public AgentInternalController(
            FoodService foodService,
            @Value("${app.agent.mcp-backend-token:}") String internalToken,
            com.dayan.food.security.AgentServiceContext contexts
    ) {
        this.foodService = foodService;
        this.internalToken = internalToken;
        this.contexts = contexts;
    }

    @GetMapping("/recommendations")
    public List<FoodVO> recommendations(
            @RequestHeader("X-MCP-Backend-Token") String token,
            @RequestParam String subjectId,
            @RequestParam String context,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "false") boolean personalized,
            @RequestParam(defaultValue = "5") int limit
    ) {
        requireInternalToken(token);
        var user=contexts.verify(context,subjectId);
        var previous=org.springframework.security.core.context.SecurityContextHolder.getContext();
        var delegated=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        var principal=com.dayan.food.security.AppUserPrincipal.from(user);
        delegated.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
        try {
            org.springframework.security.core.context.SecurityContextHolder.setContext(delegated);
            return foodService.recommend(user.getUsername(), province, city, personalized, limit);
        } finally { org.springframework.security.core.context.SecurityContextHolder.setContext(previous); }
    }

    private void requireInternalToken(String providedToken) {
        if (internalToken.isBlank() || !MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent 内部凭据无效");
        }
    }
}
