package com.example.trpg.controller;

import com.example.trpg.dto.EventLogDtos.EntryView;
import com.example.trpg.dto.EventLogDtos.SendRequest;
import com.example.trpg.service.EventLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class EventLogController {

    private final EventLogService eventLogService;

    public EventLogController(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
    }

    // 이벤트 기록 목록
    @GetMapping("/{code}/events")
    public List<EntryView> list(@PathVariable String code,
                                @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return eventLogService.getForPlayer(code, token);
    }

    // GM 전용: NPC 선택 목록
    @GetMapping("/{code}/gm/event-speakers")
    public List<String> speakers(@PathVariable String code,
                                @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return eventLogService.getSpeakersForGm(code, token);
    }

    // 대사 보내기
    @PostMapping("/{code}/events")
    public ResponseEntity<Void> send(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token,
                                    @RequestBody SendRequest request) {
        eventLogService.send(code, token, request.speaker(), request.text());
        return ResponseEntity.noContent().build();
    }

    // GM이 기록 한 줄 지우기
    @DeleteMapping("/{code}/events/{id}")
    public ResponseEntity<Void> delete(@PathVariable String code,
                                        @PathVariable long id,
                                        @RequestHeader(value = "X-Player-Token", required = false) String token) {
        eventLogService.delete(code, token, id);
        return ResponseEntity.noContent().build();
    }
}