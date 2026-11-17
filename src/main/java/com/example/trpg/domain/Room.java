package com.example.trpg.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 6)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status = RoomStatus.WAITING;

    // 시계 칸 수 (0~6)
    @Column(nullable = false)
    private int clockValue = 0;

    // 읽기 시간 종료 시각 (READING 단계에서 서버가 설정)
    private Instant readingEndsAt;

    // GM이 직접 체크하는 결말 조건
    // A. 기관실을 열고 정지 레버를 내렸는가
    // 이미 데이터가 있는 테이블에 컬럼이 추가되므로 기본값을 지정
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean engineStopped = false;

    // B. 서류를 확보하고 공개 준비를 마쳤는가
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean docsSecured = false;

    // 종료 시 정해진 결말의 키 (진행 중에는 null)
    @Column(length = 30)
    private String endingKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Room() {}

    public Room(String code) {
        this.code = code;
    }

    // 게임 시작: 읽기 단계로 전환하고 종료 시각을 기록
    public void startReading(Instant endsAt) {
        this.status = RoomStatus.READING;
        this.readingEndsAt = endsAt;
    }

    // 읽기 단계가 끝나고 본 게임으로 전환
    public void startPlaying() {
        this.status = RoomStatus.PLAYING;
    }

    // 시계 칸 수를 직접 지정 (범위 검사는 서비스에서 함)
    public void setClockValue(int clockValue) {
        this.clockValue = clockValue;
    }

    // 결말 조건을 GM이 설정
    public void setEndingConditions(boolean engineStopped, boolean docsSecured) {
        this.engineStopped = engineStopped;
        this.docsSecured = docsSecured;
    }

    // 결말을 정하고 종료 상태로 전환
    public void finish(String endingKey) {
        this.status = RoomStatus.ENDED;
        this.endingKey = endingKey;
    }

    // 방을 처음(대기) 상태로 되돌림. 새 판 시작과 개발용 강제 중단이 함께 사용
    public void resetToWaiting() {
        this.status = RoomStatus.WAITING;
        this.readingEndsAt = null;
        this.clockValue = 0;
        this.engineStopped = false;
        this.docsSecured = false;
        this.endingKey = null;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public RoomStatus getStatus() { return status; }
    public int getClockValue() { return clockValue; }
    public Instant getReadingEndsAt() { return readingEndsAt; }
    public boolean isEngineStopped() { return engineStopped; }
    public boolean isDocsSecured() { return docsSecured; }
    public String getEndingKey() { return endingKey; }
}