package com.example.trpg.controller;

import com.example.trpg.dto.GameDtos.MyRoleResponse;
import com.example.trpg.dto.RoomDtos.ClockRequest;
import com.example.trpg.event.GameStartedEvent;
import com.example.trpg.event.LobbyChangedEvent;
import com.example.trpg.service.GameService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class GameController {

    private final GameService gameService;
    private final ApplicationEventPublisher eventPublisher;

    public GameController(GameService gameService, ApplicationEventPublisher eventPublisher) {
        this.gameService = gameService;
        this.eventPublisher = eventPublisher;
    }

    // GM이 게임 시작. 토큰은 URL이 아니라 헤더로 전달
    @PostMapping("/{code}/start")
    public ResponseEntity<Void> start(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token) {
        String roomCode = gameService.startGame(code, token);
        // DB 저장이 확정된 뒤에: 방 상태 방송 → 각자에게 카드 전송
        eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));
        eventPublisher.publishEvent(new GameStartedEvent(roomCode));
        return ResponseEntity.noContent().build();
    }

    // 플레이어가 "읽었어요". 전원이 누르면 서버가 게임 중으로 전환
    @PostMapping("/{code}/ready")
    public ResponseEntity<Void> ready(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token) {
        String roomCode = gameService.markReady(code, token);
        eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));
        return ResponseEntity.noContent().build();
    }

    // GM이 읽기 시간을 건너뛰고 바로 시작
    @PostMapping("/{code}/begin")
    public ResponseEntity<Void> begin(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token) {
        String roomCode = gameService.beginPlaying(code, token);
        eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));
        return ResponseEntity.noContent().build();
    }

    // GM이 시계를 n칸으로 설정 (같은 값을 여러 번 보내도 결과가 같음)
    @PostMapping("/{code}/clock")
    public ResponseEntity<Void> setClock(@PathVariable String code,
                                        @RequestHeader(value = "X-Player-Token", required = false) String token,
                                        @RequestBody ClockRequest request) {
        String roomCode = gameService.setClock(code, token, request.value());
        eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));   // 방 정보 방송에 clockValue가 포함되어 있음
        return ResponseEntity.noContent().build();
    }

    // GM 전용: 칸별 사건 메모
    @GetMapping("/{code}/gm/clock-events")
    public List<String> clockEvents(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return gameService.getClockEvents(code, token);
    }

    // 내 역할 조회. 새로고침, 재접속 때 푸시를 놓쳤어도 다시 가져올 수 있음
    @GetMapping("/{code}/me/role")
    public MyRoleResponse myRole(@PathVariable String code,
                                @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return gameService.getMyRole(code, token);
    }
}