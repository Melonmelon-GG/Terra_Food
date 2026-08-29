package com.dayan.food.controller;

import com.dayan.food.entity.dto.AgentCommentCreateDTO;
import com.dayan.food.entity.vo.FoodCommentVO;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.service.FoodCommentService;
import com.dayan.food.service.FoodService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final FoodCommentService foodCommentService;
    private final String internalToken;

    public AgentInternalController(
            FoodService foodService,
            FoodCommentService foodCommentService,
            @Value("${app.agent.internal-token:}") String internalToken
    ) {
        this.foodService = foodService;
        this.foodCommentService = foodCommentService;
        this.internalToken = internalToken;
    }

    @GetMapping("/recommendations")
    public List<FoodVO> recommendations(
            @RequestHeader("X-Agent-Internal-Token") String token,
            @RequestParam String username,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "false") boolean personalized,
            @RequestParam(defaultValue = "5") int limit
    ) {
        requireInternalToken(token);
        return foodService.recommend(username, province, city, personalized, limit);
    }

    @PostMapping("/comments")
    public FoodCommentVO createComment(
            @RequestHeader("X-Agent-Internal-Token") String token,
            @Valid @RequestBody AgentCommentCreateDTO request
    ) {
        requireInternalToken(token);
        return foodCommentService.create(request.foodId(), request.content(), request.username());
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
