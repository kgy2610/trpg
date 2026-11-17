package com.example.trpg.controller;

import com.example.trpg.dto.EndingDtos.ConditionsRequest;
import com.example.trpg.dto.EndingDtos.EndingResult;
import com.example.trpg.dto.EndingDtos.EndingState;
import com.example.trpg.event.LobbyChangedEvent;
import com.example.trpg.service.EndingService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class EndingController {

    private final EndingService endingService;
    private final ApplicationEventPublisher eventPublisher;

    public EndingController(EndingService endingService, ApplicationEventPublisher eventPublisher) {
        this.endingService = endingService;
        this.eventPublisher = eventPublisher;
    }

    // GM이 결말 조건을 설정 (플레이어에게 방송하지 않음)
    @PostMapping("/{code}/ending/conditions")
    public ResponseEntity<Void> setConditions(@PathVariable String code,
                                            @RequestHeader(value = "X-Player-Token", required = false) String token,
                                            @RequestBody ConditionsRequest request) {
        endingService.setConditions(code, token, request.engineStopped(), request.docsSecured());
        return ResponseEntity.noContent().build();
    }

    // GM 전용: 현재 조건과 결말 미리 보기
    @GetMapping("/{code}/gm/ending")
    public EndingState state(@PathVariable String code,
                            @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return endingService.getState(code, token);
    }

    // GM이 결말 진행. 방 상태가 ENDED로 방송되면 모든 화면이 결말 화면으로 바뀜
    @PostMapping("/{code}/ending/finish")
    public ResponseEntity<Void> finish(@PathVariable String code,
                                        @RequestHeader(value = "X-Player-Token", required = false) String token) {
        String roomCode = endingService.finish(code, token);
        eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));
        return ResponseEntity.noContent().build();
    }

    // 종료 후 결말, 후일담, 정체 공개. 종료 전에는 거부됨
    @GetMapping("/{code}/ending")
    public EndingResult result(@PathVariable String code,
                                @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return endingService.getResult(code, token);
    }

    // GM이 새 판 시작. 종료 상태에서만 가능하고, 방 상태가 WAITING으로 방송되면 모든 화면이 대기실로 이동함
    @PostMapping("/{code}/ending/restart")
    public ResponseEntity<Void> restart(@PathVariable String code,
                                        @RequestHeader(value = "X-Player-Token", required = false) String token) {
        String roomCode = endingService.restart(code, token);
        eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));
        return ResponseEntity.noContent().build();
    }
}