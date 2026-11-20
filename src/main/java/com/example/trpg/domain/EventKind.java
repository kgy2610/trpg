package com.example.trpg.domain;

public enum EventKind {
    SYSTEM,   // 시계/단서 공개 때 서버가 자동으로 남기는 기록
    NPC,      // GM이 NPC 이름으로 입력한 대사
    PLAYER    // 플레이어가 자기 캐릭터로 입력한 말
}