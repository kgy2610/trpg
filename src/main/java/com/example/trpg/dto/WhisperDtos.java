package com.example.trpg.dto;

public class WhisperDtos {

    private WhisperDtos() {}

    // GM이 보내는 요청: 받는 사람의 닉네임과 내용
    public record SendWhisperRequest(String nickname, String text) {}

    // 플레이어가 받는 귓속말 (본인에게 온 것만)
    public record WhisperView(long id, String text, long createdAtMillis) {}

    // GM이 보는 보낸 기록 (누구에게 보냈는지 포함)
    public record GmWhisperView(long id, String recipientNickname, String recipientCharacter,
                                String text, long createdAtMillis) {}
}