package com.example.trpg.controller;

import com.example.trpg.dto.WhisperDtos.GmWhisperView;
import com.example.trpg.dto.WhisperDtos.SendWhisperRequest;
import com.example.trpg.dto.WhisperDtos.WhisperView;
import com.example.trpg.event.WhisperSentEvent;
import com.example.trpg.service.WhisperService;
import com.example.trpg.service.WhisperService.SendResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class WhisperController {

    private final WhisperService whisperService;
    private final ApplicationEventPublisher eventPublisher;

    public WhisperController(WhisperService whisperService, ApplicationEventPublisher eventPublisher) {
        this.whisperService = whisperService;
        this.eventPublisher = eventPublisher;
    }

    // GM이 귓속말 보내기
    @PostMapping("/{code}/whispers")
    public ResponseEntity<Void> send(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token,
                                    @RequestBody SendWhisperRequest request) {
        SendResult result = whisperService.send(code, token, request.nickname(), request.text());
        // DB 저장이 확정된 뒤에 받는 사람과 GM에게만 전송
        eventPublisher.publishEvent(new WhisperSentEvent(
                result.roomCode(), result.recipientPlayerId(), result.gmPlayerId()));
        return ResponseEntity.noContent().build();
    }

    // 내가 받은 귓속말 (새로고침 때 사용). 본인 것만 반환됨
    @GetMapping("/{code}/whispers")
    public List<WhisperView> mine(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return whisperService.getMine(code, token);
    }

    // GM 전용: 보낸 전체 기록
    @GetMapping("/{code}/gm/whispers")
    public List<GmWhisperView> all(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return whisperService.getAllForGm(code, token);
    }
}