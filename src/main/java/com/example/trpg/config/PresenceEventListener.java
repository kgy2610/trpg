package com.example.trpg.config;

import com.example.trpg.event.LobbyChangedEvent;
import com.example.trpg.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class PresenceEventListener {

    private static final Logger log = LoggerFactory.getLogger(PresenceEventListener.class);

    private final PresenceService presenceService;
    private final ApplicationEventPublisher eventPublisher;

    public PresenceEventListener(PresenceService presenceService,
                                ApplicationEventPublisher eventPublisher) {
        this.presenceService = presenceService;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        //ifPresent 안에서 방 전체에 알림
        presenceService.disconnect(event.getSessionId()).ifPresent(offline -> {
            log.info("접속 종료: playerId={} [오프라인 전환]", offline.playerId());
            eventPublisher.publishEvent(new LobbyChangedEvent(offline.roomCode()));
        });
    }
}