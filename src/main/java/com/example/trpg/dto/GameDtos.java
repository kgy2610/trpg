package com.example.trpg.dto;

import java.util.List;

public class GameDtos {

    private GameDtos() {}

    // 캐릭터 카드 (플레이어용, 스포일러 없음)
    public record CharacterCard(String key, String name, String job, String concept,
                                int strength, int mind, int charm, List<String> abilities) {

        public CharacterCard withName(String newName) {
            return new CharacterCard(key, newName, job, concept, strength, mind, charm, abilities);
        }
    }

    // 비밀 카드
    public record SecretCard(String type, String title, String description) {}

    // GM이 보는 배정표 한 줄
    public record AssignmentInfo(String nickname, String characterName, String secretTitle) {}

    // 내 역할 응답. gm=true면 assignments만, false면 character와 secret만 채워짐
    // 시간은 날짜 형식 문제를 피하려고 밀리초 숫자로 전달 (serverNowMillis는 화면의 카운트다운 오차 보정용)
    public record MyRoleResponse(boolean gm, CharacterCard character, SecretCard secret,
                                List<AssignmentInfo> assignments, String status,
                                Long readingEndsAtMillis, long serverNowMillis) {}
}