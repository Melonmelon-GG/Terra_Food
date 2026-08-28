package com.dayan.food.controller;

import com.dayan.food.entity.dto.AgentChatDTO;
import com.dayan.food.entity.vo.AgentChatVO;
import com.dayan.food.service.AgentGatewayService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentGatewayService agentGatewayService;

    public AgentController(AgentGatewayService agentGatewayService) {
        this.agentGatewayService = agentGatewayService;
    }

    @PostMapping("/chat")
    public AgentChatVO chat(
            @Valid @RequestBody AgentChatDTO request,
            Authentication authentication
    ) {
        return agentGatewayService.chat(
                authentication.getName(),
                request.message(),
                request.availableTracks(),
                request.currentFoodId()
        );
    }
}
