package com.dayan.food.service;

import com.dayan.food.entity.vo.AgentChatVO;

import java.util.List;

public interface AgentGatewayService {

    AgentChatVO chat(String username, String message, List<String> availableTracks, Long currentFoodId);
}
