package com.example.trpg.service;

import com.example.trpg.event.WhisperSentEvent;
import com.example.trpg.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WhisperDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(WhisperDeliveryListener.class);

    private final WhisperService whisperService;
    private final SimpMessagingTemplate messagingTemplate;

    public WhisperDeliveryListener(WhisperService whisperService, SimpMessagingTemplate messagingTemplate) {
        this.whisperService = whisperService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onWhisperSent(WhisperSentEvent event) {
        try {
            // 받는 사람: 본인이 받은 귓속말 목록만 (개인 채널이라 그 사람의 연결에만 전달됨)
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(event.recipientPlayerId()), "/queue/whisper",
                    whisperService.listForRecipient(event.recipientPlayerId()));

            // GM: 보낸 전체 기록 (GM의 개인 채널)
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(event.gmPlayerId()), "/queue/whisper-log",
                    whisperService.listAllForGm(event.roomCode()));
        } catch (ApiException e) {
            log.warn("귓속말 전송 실패: {}", e.getMessage());
        }
    }
}