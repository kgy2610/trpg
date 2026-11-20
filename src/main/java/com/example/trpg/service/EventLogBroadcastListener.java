package com.example.trpg.service;

import com.example.trpg.event.EventLogChangedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EventLogBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;

    public EventLogBroadcastListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChanged(EventLogChangedEvent event) {
        messagingTemplate.convertAndSend("/topic/room/" + event.roomCode() + "/events", event.change());
    }
}