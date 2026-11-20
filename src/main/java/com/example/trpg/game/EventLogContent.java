package com.example.trpg.game;

import java.util.List;

// 이벤트 기록에 쓰는 고정 내용
public final class EventLogContent {

    private EventLogContent() {}

    // 한 줄 최대 글자 수
    public static final int MAX_LENGTH = 200;
    // 같은 사람이 연속으로 보낼 수 있는 최소 간격
    public static final long MIN_INTERVAL_MILLIS = 1000;

    // GM이 고를 수 있는 NPC 이름
    private static final List<String> NPC_SPEAKERS = List.of(
            "차장 주민식", "견습생 최민부", "노부인 윤말순",
            "승객1", "승객2", "승객3", "방송");

    public static List<String> npcSpeakers() {
        return NPC_SPEAKERS;
    }

    // 시계가 처음 도달했을 때 모두에게 공개되는 문장
    private static final List<String> CLOCK_SENTENCES = List.of(
            "조명이 깜빡이고 승객들이 웅성거리기 시작했다.",
            "식당칸에서 승객들이 다투기 시작했다.",
            "쓰러진 차장의 숨이 약해지고 있다.",
            "방송이 다시 흘러나왔다. 곧 열차가 출발할 것 같다.",
            "열차가 진동하기 시작했다. 열차가 출발하기 직전인 것 같다.",
            "열차가 출발했다.");

    // n칸에 처음 도달했을 때의 문장
    public static String clockSentence(int clockValue) {
        if (clockValue < 1 || clockValue > CLOCK_SENTENCES.size()) {
            return null;
        }
        return CLOCK_SENTENCES.get(clockValue - 1);
    }
}