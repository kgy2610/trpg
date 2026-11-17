package com.example.trpg.controller;

import com.example.trpg.dto.DiceDtos.RollRequest;
import com.example.trpg.dto.DiceDtos.RollView;
import com.example.trpg.event.DiceRolledEvent;
import com.example.trpg.service.DiceService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class DiceController {

    private final DiceService diceService;
    private final ApplicationEventPublisher eventPublisher;

    public DiceController(DiceService diceService, ApplicationEventPublisher eventPublisher) {
        this.diceService = diceService;
        this.eventPublisher = eventPublisher;
    }

    // 플레이어가 주사위를 굴림. 결과는 응답이 아니라 방송으로 모두에게 동시에 전달됨
    @PostMapping("/{code}/dice/roll")
    public ResponseEntity<Void> roll(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token,
                                    @RequestBody RollRequest request) {
        String roomCode = diceService.roll(code, token, request.stat(), request.reason());
        // DB 저장이 확정된 뒤에 방 전체에 최근 판정 로그를 방송
        eventPublisher.publishEvent(new DiceRolledEvent(roomCode));
        return ResponseEntity.noContent().build();
    }

    // 최근 판정 로그 (새로고침 때 사용). 이 방의 참가자만 가능
    @GetMapping("/{code}/dice")
    public List<RollView> recent(@PathVariable String code,
                                @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return diceService.getRecentForPlayer(code, token);
    }
}