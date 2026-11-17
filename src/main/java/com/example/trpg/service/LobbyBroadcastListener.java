package com.example.trpg.service;

import com.example.trpg.dto.RoomDtos.RoomInfo;
import com.example.trpg.event.LobbyChangedEvent;
import com.example.trpg.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class LobbyBroadcastListener {

    private static final Logger log = LoggerFactory.getLogger(LobbyBroadcastListener.class);

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public LobbyBroadcastListener(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onLobbyChanged(LobbyChangedEvent event) {
        try {
            RoomInfo info = roomService.getRoom(event.roomCode());
            messagingTemplate.convertAndSend("/topic/room/" + event.roomCode() + "/lobby", info);
        } catch (ApiException e) {
            log.warn("대기실 방송 실패: {}", e.getMessage());
        }
    }
}