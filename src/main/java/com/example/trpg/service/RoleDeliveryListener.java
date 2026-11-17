package com.example.trpg.service;

import com.example.trpg.event.GameStartedEvent;
import com.example.trpg.exception.ApiException;
import com.example.trpg.service.GameService.RoleDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RoleDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(RoleDeliveryListener.class);

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public RoleDeliveryListener(GameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onGameStarted(GameStartedEvent event) {
        try {
            for (RoleDelivery delivery : gameService.loadRoleDeliveries(event.roomCode())) {
                // 대상은 playerId. 개인 채널이라 그 사람의 연결에만 전달됨
                messagingTemplate.convertAndSendToUser(
                        String.valueOf(delivery.playerId()), "/queue/role", delivery.payload());
            }
        } catch (ApiException e) {
            log.warn("역할 전송 실패: {}", e.getMessage());
        }
    }
}