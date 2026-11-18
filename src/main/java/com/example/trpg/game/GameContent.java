package com.example.trpg.game;

import com.example.trpg.domain.Player;
import com.example.trpg.dto.GameDtos.CharacterCard;
import com.example.trpg.dto.GameDtos.SecretCard;

import java.util.List;

public final class GameContent {

        private GameContent() {}

        public static final List<String> SURNAMES = List.of("정", "진", "이토", "이", "김");

        public static String displayName(Player player) {
                String surname = player.getSurname();
                return (surname == null || surname.isBlank())
                        ? player.getNickname()
                        : surname + " " + player.getNickname();
        }

    // 캐릭터 카드(직업, 콘셉트, 능력치). 이름은 플레이어마다 달라서 여기서는 비워 두고 서비스가 withName으로 채움
        public static CharacterCard character(GameCharacter c) {
                return switch (c) {
                case FIREFIGHTER -> new CharacterCard(c.name(), "", "소방관 출신 헬스 트레이너",
                        "위기 앞에서는 몸이 먼저 움직이는 사람. 차분하고 말수가 적다.",
                        2, 0, 1,
                        List.of("다친 사람에게 응급처치를 할 수 있다",
                                "잠긴 문이나 상자를 힘으로 열 수 있다 (대신 시계 +1)",
                                "소란이 커지기 전에 몸으로 진정시킬 수 있다"));
                case RAIL_FAN -> new CharacterCard(c.name(), "", "철도 덕후 대학생",
                        "철도 용어와 계기판만 보면 눈이 반짝이는 사람. 흥분하면 말이 빨라진다.",
                        1, 2, 0,
                        List.of("철도 용어와 기계 장치의 구조를 안다",
                                "계기판이나 장치를 읽고 위치와 쓰임을 파악할 수 있다"));
                case SALESPERSON -> new CharacterCard(c.name(), "", "영업 사원",
                        "말투와 눈빛으로 거짓말을 읽어내는 사람.",
                        1, 0, 2,
                        List.of("수상한 말을 하는 사람을 알아챈다",
                                "설득과 거래로 사람을 움직일 수 있다",
                                "흥분한 사람을 달래 분위기를 가라앉힐 수 있다"));
                case REPORTER -> new CharacterCard(c.name(), "", "사회부 기자",
                        "냄새나는 이야기는 놓치지 않는 사람. 늘 수첩에 메모한다.",
                        1, 1, 1,
                        List.of("기록과 수치를 대조해 어긋난 점을 찾는다",
                                "흩어진 단서를 하나의 사건으로 연결한다",
                                "문서가 가진 의미와 가치를 알아본다"));
                case MUSICIAN -> new CharacterCard(c.name(), "", "거리 연주자 (절대음감)",
                        "소리 하나로 방의 크기도, 거짓말도 알아내는 귀를 가졌다.",
                        0, 2, 1,
                        List.of("소리의 이상한 점을 알아챈다",
                                "어두운 곳에서도 소리로 위치와 방향을 가늠한다",
                                "기계나 열차가 내는 소리로 상태를 짐작한다"));
                };
        }

        public static SecretCard secret(SecretRole r) {
                return switch (r) {
                case PASSENGER -> new SecretCard(r.name(), "승객",
                                "당신은 승객 명단에 있습니다.\n\n"
                                + "목표: 열차가 다시 출발하지 않도록 막고, 모두가 무사히 내리게 하세요.\n\n"
                                + "누군가는 승객 명단에 없습니다.");
                case STOWAWAY -> new SecretCard(r.name(), "밀항자",
                                "당신은 승객 명단에 없습니다. 승차권은 위조했습니다.\n\n"
                                + "사실 당신은 이 열차 운영사의 제동 장치 결함 은폐를 고발하려는 내부고발자입니다. "
                                + "증거 서류를 들고 몰래 탔고, 서류는 화물칸의 잠긴 상자에 숨겨 두었습니다. "
                                + "방송을 듣고 누군가 자신을 쫓고 있다고 믿고 있습니다.\n\n"
                                + "목표: 서류를 지키고, 열차가 출발하지 않게 하세요.\n"
                                + "정체는 숨겨도 됩니다. 다만 시계 4칸 이후 스스로 밝히면 서류를 가장 안전하게 전달할 수 있습니다.");
                };
        }

        // 시계 최대 칸 수
        public static final int CLOCK_MAX = 6;

        // GM 전용: 시계가 n칸에 도달했을 때 일어나는 사건 (index 0이 1칸). 플레이어에게는 절대 보내지 않음
        public static List<String> clockEvents() {
                return List.of(
                        "1칸: 조명이 깜빡이고 승객들이 웅성거리기 시작한다.",
                        "2칸: 식당칸에서 승객끼리 다툼이 벌어진다. 소방관 출신 캐릭터가 몸으로 진정시킬 기회.",
                        "3칸: 쓰러진 차장의 숨이 약해진다. 이 칸이 끝나기 전에 응급처치(소방관 출신 캐릭터만 가능)하지 못하면 차장은 더 이상 말할 수 없다.",
                        "4칸: 방송이 다시 재생된다. 출발까지 약 20분. 밀항자는 이때부터 스스로 정체를 밝힐 수 있다.",
                        "5칸: 열차가 진동하며 엔진 예열음이 난다. 출발까지 약 10분. 거리 연주자 캐릭터가 남은 시간을 정확히 짚어 줄 수 있다.",
                        "6칸: 열차가 출발한다. 결말을 판정한다."
                );
        }
}