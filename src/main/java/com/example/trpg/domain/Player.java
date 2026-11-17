package com.example.trpg.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "player",
    uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "nickname"})
)
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false)
    private boolean gm;

    // 본인 확인용 토큰 (이후 WebSocket 인증에 사용)
    @Column(nullable = false, unique = true, length = 36)
    private String token;

    // 게임 시작 시 배정 (그 전에는 null)
    private String characterKey;
    private String secretRole;

    // GM이 게임 중 재접속을 허용했는지 (입장에 한 번 쓰이면 자동으로 false로 돌아감)
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean rejoinAllowed = false;

    // "읽었어요"를 눌렀는지 (읽기 단계에서만 의미가 있음)
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean ready = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Player() {}

    public Player(Room room, String nickname, boolean gm, String token) {
        this.room = room;
        this.nickname = nickname;
        this.gm = gm;
        this.token = token;
    }

    // 자리를 다시 인수할 때 토큰을 새로 발급 (이전 토큰은 무효가 됨)
    public void changeToken(String newToken) {
        this.token = newToken;
    }

    // 게임 시작 시 캐릭터와 비밀 카드를 배정 (값은 enum의 이름 문자열)
    public void assign(String characterKey, String secretRole) {
        this.characterKey = characterKey;
        this.secretRole = secretRole;
    }

    public void setRejoinAllowed(boolean rejoinAllowed) {
        this.rejoinAllowed = rejoinAllowed;
    }

    public void markReady() {
        this.ready = true;
    }

    // 개발용: 역할 배정과 진행 상태를 모두 초기화
    public void resetGame() {
        this.characterKey = null;
        this.secretRole = null;
        this.ready = false;
        this.rejoinAllowed = false;
    }

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public String getNickname() { return nickname; }
    public boolean isGm() { return gm; }
    public String getToken() { return token; }
    public String getCharacterKey() { return characterKey; }
    public String getSecretRole() { return secretRole; }
    public boolean isRejoinAllowed() { return rejoinAllowed; }
    public boolean isReady() { return ready; }
}