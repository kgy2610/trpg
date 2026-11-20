package com.example.trpg.game;

import java.util.List;
import java.util.Optional;

public final class ClueContent {

        private ClueContent() {}

        public record Clue(String key, String location, String title, String content) {}

        // 표시 순서는 이 목록 순서를 따름
        private static final List<Clue> ALL = List.of(
                // 1호차 객실
                new Clue("LUGGAGE", "1호차 객실", "주인 없는 낡은 가방",
                        "주인 없는 낡은 가방이 객실 선반에 놓여 있다. 승차권은 없고 안은 거의 비어 있다. 승차권 없이 탄 사람이 있다는 뜻일지도 모른다."),
                new Clue("BROADCAST_EDIT", "1호차 객실", "방송의 편집 흔적",
                        "객실 천장 스피커에서 나온 방송을 다시 떠올려 보면, 문장 사이에 소리가 이어 붙은 흔적이 있다. 방송은 누군가 그 자리에서 한 것이 아니라 미리 녹음된 것이다."),

                // 식당칸
                new Clue("PASSENGER_LIST", "식당칸", "차장의 승객 명단 수첩",
                        "식당칸 통로에 떨어진 차장의 승객 명단 수첩. 적힌 이름은 7명인데, 칸마다 사람을 세어 보면 실제 승객은 8명이다."),
                new Clue("CONDUCTOR_MEMO", "식당칸", "차장의 메모",
                        "수첩 뒷장에 급하게 휘갈겨 쓴 메모. '제동 압력 이상. 본사 무시. 출발 금지.'"),
                new Clue("LOCK_CODE", "식당칸", "수첩 구석의 숫자",
                        "수첩 맨 뒷장 구석에 네 자리 숫자가 적혀 있다. 7 · 1 · 0 · 3."),
                new Clue("CONDUCTOR_KEY", "식당칸", "차장의 열쇠",
                        "쓰러진 차장의 주머니에서 나온 열쇠. 기관실 문의 자물쇠 하나에 맞는 것 같다."),

                // 화물칸
                new Clue("LOCKED_BOX", "화물칸", "잠긴 철제 상자",
                        "번호 자물쇠가 달린 철제 상자가 화물칸 구석에 있다. 번호를 맞추거나 힘으로 부수어야 열린다."),
                new Clue("COVERUP_DOCS", "화물칸", "결함 은폐 서류",
                        "상자 안에 서류 뭉치가 있다. 3년치 제동 장치 점검 기록과 운영사의 내부 메일이다. 결함을 알면서도 운행을 이어 왔다는 증거다."),

                // 기관실
                new Clue("ENGINE_DOOR", "기관실", "기관실 철문",
                        "기관실의 철문. 자물쇠가 두 개 나란히 달려 있어 열쇠가 두 개 필요해 보인다. 힘으로 부수면 시간이 오래 걸린다."),
                new Clue("CONTROL_PANEL", "기관실", "계기판과 정지 레버",
                        "문의 작은 창으로 계기판이 보인다. 제동 압력 게이지가 위험 구간에 걸려 있다. 정지 레버는 붉은 손잡이의 레버다.")
        );

        public static List<Clue> all() {
                return ALL;
        }

        public static Optional<Clue> find(String key) {
                return ALL.stream().filter(c -> c.key().equals(key)).findFirst();
        }
}