package com.example.trpg.controller;

import com.example.trpg.dto.RoomDtos.JoinResponse;
import com.example.trpg.dto.RoomDtos.KickRequest;
import com.example.trpg.dto.RoomDtos.NicknameRequest;
import com.example.trpg.dto.RoomDtos.RejoinPermissionRequest;
import com.example.trpg.dto.RoomDtos.RoomInfo;
import com.example.trpg.event.LobbyChangedEvent;
import com.example.trpg.service.RoomService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final ApplicationEventPublisher eventPublisher;

    public RoomController(RoomService roomService, ApplicationEventPublisher eventPublisher) {
        this.roomService = roomService;
        this.eventPublisher = eventPublisher;
    }

    // 방 만들기 (만든 사람이 GM)
    @PostMapping
    public ResponseEntity<JoinResponse> create(@RequestBody NicknameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(request.nickname()));
    }

    // 방 입장
    @PostMapping("/{code}/join")
    public JoinResponse join(@PathVariable String code, @RequestBody NicknameRequest request) {
        JoinResponse response = roomService.joinRoom(code, request.nickname());
        eventPublisher.publishEvent(new LobbyChangedEvent(response.code()));
        return response;
    }

    // 나가기 (본인 토큰으로만 가능). 토큰은 URL이 아니라 헤더로 전달
    @DeleteMapping("/{code}/players/me")
    public ResponseEntity<Void> leave(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token) {
        String roomCode = roomService.leaveRoom(code, token);
        eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));
        return ResponseEntity.noContent().build();
    }

    // GM이 오프라인 참가자 내보내기. 닉네임에 특수문자가 있어도 안전하도록 URL이 아니라 본문으로 받음
    @PostMapping("/{code}/kick")
    public ResponseEntity<Void> kick(@PathVariable String code,
                                    @RequestHeader(value = "X-Player-Token", required = false) String token,
                                    @RequestBody KickRequest request) {
        String roomCode = roomService.kickPlayer(code, token, request.nickname());
        eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));
        return ResponseEntity.noContent().build();
    }

    // 게임 중 오프라인 참가자의 재접속을 허용/해제
    @PostMapping("/{code}/rejoin-permission")
    public ResponseEntity<Void> setRejoinPermission(@PathVariable String code,
                                                    @RequestHeader(value = "X-Player-Token", required = false) String token,
                                                    @RequestBody RejoinPermissionRequest request) {
        String roomCode = roomService.setRejoinPermission(code, token, request.nickname(), request.allowed());
        eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));
        return ResponseEntity.noContent().build();
    }

    // 방 정보 (대기실 표시용, 토큰은 포함하지 않음)
    @GetMapping("/{code}")
    public RoomInfo get(@PathVariable String code) {
        return roomService.getRoom(code);
    }
}