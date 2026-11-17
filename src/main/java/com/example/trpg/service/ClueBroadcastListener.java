package com.example.trpg.service;

import com.example.trpg.dto.ClueDtos.ClueView;
import com.example.trpg.event.CluesChangedEvent;
import com.example.trpg.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClueBroadcastListener {

    private static final Logger log = LoggerFactory.getLogger(ClueBroadcastListener.class);

    private final ClueService clueService;
    private final SimpMessagingTemplate messagingTemplate;

    public ClueBroadcastListener(ClueService clueService, SimpMessagingTemplate messagingTemplate) {
        this.clueService = clueService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onCluesChanged(CluesChangedEvent event) {
        try {
            List<ClueView> revealed = clueService.listRevealed(event.roomCode());
            // 방 채널은 이미 "내 방의 채널만 구독 가능" 규칙이 적용되어 있어서 구독 허용 목록을 따로 늘릴 필요가 없음
            messagingTemplate.convertAndSend("/topic/room/" + event.roomCode() + "/clues", revealed);
        } catch (ApiException e) {
            log.warn("단서 방송 실패: {}", e.getMessage());
        }
    }
}