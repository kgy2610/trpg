package com.example.trpg.service;

import com.example.trpg.dto.DiceDtos.RollView;
import com.example.trpg.event.DiceRolledEvent;
import com.example.trpg.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiceBroadcastListener {

    private static final Logger log = LoggerFactory.getLogger(DiceBroadcastListener.class);

    private final DiceService diceService;
    private final SimpMessagingTemplate messagingTemplate;

    public DiceBroadcastListener(DiceService diceService, SimpMessagingTemplate messagingTemplate) {
        this.diceService = diceService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onDiceRolled(DiceRolledEvent event) {
        try {
            List<RollView> recent = diceService.listRecent(event.roomCode());
            // 방 채널은 이미 "내 방의 채널만 구독 가능" 규칙이 적용되어 있어서 구독 허용 목록을 따로 늘릴 필요가 없음
            messagingTemplate.convertAndSend("/topic/room/" + event.roomCode() + "/dice", recent);
        } catch (ApiException e) {
            log.warn("주사위 방송 실패: {}", e.getMessage());
        }
    }
}