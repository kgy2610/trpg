package com.example.trpg.controller;

import com.example.trpg.dto.ClueDtos.ClueRevealRequest;
import com.example.trpg.dto.ClueDtos.ClueView;
import com.example.trpg.dto.ClueDtos.GmClueView;
import com.example.trpg.event.CluesChangedEvent;
import com.example.trpg.service.ClueService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class ClueController {

    private final ClueService clueService;
    private final ApplicationEventPublisher eventPublisher;

    public ClueController(ClueService clueService, ApplicationEventPublisher eventPublisher) {
        this.clueService = clueService;
        this.eventPublisher = eventPublisher;
    }

    // 공개된 단서 목록 (새로고침 때 사용). 이 방의 참가자만 가능
    @GetMapping("/{code}/clues")
    public List<ClueView> revealed(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return clueService.getRevealedForPlayer(code, token);
    }

    // GM 전용: 전체 단서와 공개 여부
    @GetMapping("/{code}/gm/clues")
    public List<GmClueView> all(@PathVariable String code,
                                @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return clueService.getAllForGm(code, token);
    }

    // GM이 단서 공개/숨기기. 키는 URL이 아니라 본문으로 받음
    @PostMapping("/{code}/clues/reveal")
    public ResponseEntity<Void> reveal(@PathVariable String code,
                                        @RequestHeader(value = "X-Player-Token", required = false) String token,
                                        @RequestBody ClueRevealRequest request) {
        String roomCode = clueService.setRevealed(code, token, request.key(), request.revealed());
        // DB 저장이 확정된 뒤에 방 전체에 공개된 목록을 방송
        eventPublisher.publishEvent(new CluesChangedEvent(roomCode));
        return ResponseEntity.noContent().build();
    }
}