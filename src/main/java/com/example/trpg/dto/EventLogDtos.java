package com.example.trpg.dto;

public class EventLogDtos {

    private EventLogDtos() {}

    // 대사 보내기 요청
    public record SendRequest(String speaker, String text) {}

    // 기록 한 줄 (모두에게 공개되는 정보)
    // kind: SYSTEM / NPC / PLAYER,  speaker: 자동 기록이면 null
    public record EntryView(long id, String kind, String speaker, String text, long createdAtMillis) {}

    // 방송 내용
    public record Change(String type, EntryView entry, Long deletedId) {}
}