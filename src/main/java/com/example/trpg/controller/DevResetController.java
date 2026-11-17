package com.example.trpg.controller;

import com.example.trpg.event.LobbyChangedEvent;
import com.example.trpg.service.DevResetService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")   // 개발 환경(local)에서만 이 API가 존재함
@RequestMapping("/api/rooms")
public class DevResetController {

    private final DevResetService devResetService;
    private final ApplicationEventPublisher eventPublisher;

    public DevResetController(DevResetService devResetService, ApplicationEventPublisher eventPublisher) {
        this.devResetService = devResetService;
        this.eventPublisher = eventPublisher;
    }

    // GM이 게임을 강제 중단. 방 상태가 WAITING으로 방송되면 모든 화면이 대기실로 이동함
    @PostMapping("/{code}/dev/reset")
    public ResponseEntity<Void> reset(@PathVariable String code,
                                        @RequestHeader(value = "X-Player-Token", required = false) String token) {
        String roomCode = devResetService.resetGame(code, token);
        eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));
        return ResponseEntity.noContent().build();
    }
}