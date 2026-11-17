package com.example.trpg.dto;

public class ClueDtos {

    private ClueDtos() {}

    // 공개된 단서 (플레이어도 볼 수 있음)
    public record ClueView(String key, String location, String title, String content) {}

    // GM 전용: 전체 단서와 공개 여부
    public record GmClueView(String key, String location, String title, String content, boolean revealed) {}

    // 공개(revealed=true) 또는 숨기기(revealed=false) 요청
    public record ClueRevealRequest(String key, boolean revealed) {}
}