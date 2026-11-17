package com.example.trpg.dto;

import com.example.trpg.dto.GameDtos.AssignmentInfo;

import java.util.List;

public class EndingDtos {

    private EndingDtos() {}

    // GM이 조건을 맞추는 요청
    public record ConditionsRequest(boolean engineStopped, boolean docsSecured) {}

    // GM 전용: 지금 조건과, 이 조건이면 어떤 결말이 되는지 미리 보기
    public record EndingState(boolean engineStopped, boolean docsSecured, String endingKey, String endingTitle) {}

    // 종료 후 모두에게 공개: 결말, 후일담, 정체 공개(전원의 캐릭터와 비밀 카드)
    public record EndingResult(String key, String title, String epilogue,
                                String stowawayNickname, List<AssignmentInfo> assignments) {}
}