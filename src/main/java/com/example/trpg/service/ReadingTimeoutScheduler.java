package com.example.trpg.service;

import com.example.trpg.event.LobbyChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReadingTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReadingTimeoutScheduler.class);

    private final GameService gameService;
    private final ApplicationEventPublisher eventPublisher;

    public ReadingTimeoutScheduler(GameService gameService, ApplicationEventPublisher eventPublisher) {
        this.gameService = gameService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 2000)
    public void advanceExpiredRooms() {
        try {
            // 전환이 DB에 확정된 뒤에 방송 (서비스 메서드가 끝나면 커밋됨)
            for (String roomCode : gameService.advanceExpiredRooms()) {
                log.info("읽기 시간 종료: 방 {} → 게임 중", roomCode);
                eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));
            }
        } catch (Exception e) {
            // 한 번 실패해도 다음 점검에서 다시 시도하도록 예외를 삼키고 기록만 남김
            log.warn("읽기 시간 점검 중 오류: {}", e.getMessage());
        }
    }
}