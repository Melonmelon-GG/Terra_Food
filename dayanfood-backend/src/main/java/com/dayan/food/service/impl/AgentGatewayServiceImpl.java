package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.AgentRuntimeRequestDTO;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.vo.AgentChatVO;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.AgentGatewayService;
import com.dayan.food.service.FoodService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@Service
public class AgentGatewayServiceImpl implements AgentGatewayService {

    private final AppUserMapper appUserMapper;
    private final FoodService foodService;
    private final RestClient restClient;
    private final String agentServiceUrl;
    private final String internalToken;

    public AgentGatewayServiceImpl(
            AppUserMapper appUserMapper,
            FoodService foodService,
            RestClient.Builder restClientBuilder,
            @Value("${app.agent.service-url:http://localhost:8090}") String agentServiceUrl,
            @Value("${app.agent.internal-token:}") String internalToken
    ) {
        this.appUserMapper = appUserMapper;
        this.foodService = foodService;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.agentServiceUrl = agentServiceUrl;
        this.internalToken = internalToken;
    }

    @Override
    public AgentChatVO chat(String username, String message, List<String> availableTracks, Long currentFoodId) {
        if (internalToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Agent 尚未配置");
        }
        AppUser user = appUserMapper.findByUsername(username);
        if (user == null || !user.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在或已停用");
        }

        FoodVO currentFood = currentFoodId == null ? null : foodService.detail(currentFoodId);

        try {
            AgentChatVO response = restClient.post()
                    .uri(agentServiceUrl + "/chat")
                    .header("X-Agent-Internal-Token", internalToken)
                    .body(new AgentRuntimeRequestDTO(
                            username,
                            user.getDisplayName(),
                            message.trim(),
                            availableTracks == null ? List.of() : availableTracks,
                            currentFood == null ? null : currentFood.id(),
                            currentFood == null ? null : currentFood.name()
                    ))
                    .retrieve()
                    .body(AgentChatVO.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent 返回了空响应");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent 服务暂时不可用", exception);
        }
    }
}
