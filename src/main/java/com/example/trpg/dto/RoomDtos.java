package com.example.trpg.dto;

import java.util.List;

public class RoomDtos {

    private RoomDtos() {}

    public record NicknameRequest(String nickname) {}

    // 내보낼 참가자의 닉네임
    public record KickRequest(String nickname) {}

    // 재접속 허용/해제 요청 (allowed=true면 허용, false면 해제)
    public record RejoinPermissionRequest(String nickname, boolean allowed) {}

    // 방 생성/입장 공통 응답 (token은 본인에게만 전달됨)
    public record JoinResponse(String code, String nickname, boolean gm, String token) {}

    public record PlayerInfo(String nickname, boolean gm, boolean online, boolean rejoinAllowed, boolean ready) {}   // [수정] ready 추가

    public record RoomInfo(String code, String status, int maxPlayers, List<PlayerInfo> players) {}
}