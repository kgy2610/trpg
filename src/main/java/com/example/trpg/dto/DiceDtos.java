package com.example.trpg.dto;

public class DiceDtos {

    private DiceDtos() {}

    // 굴리기 요청: 능력치(STRENGTH, MIND, CHARM)와 행동 설명(선택)
    public record RollRequest(String stat, String reason) {}

    // 판정 로그 한 줄 (모두에게 공개되는 정보)
    public record RollView(long id, String nickname, String characterName,
                            String stat, String statLabel,
                            int die1, int die2, int modifier, int total,
                            String outcome, String outcomeLabel,
                            String reason, long createdAtMillis) {}
}