package com.example.trpg.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="room")

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

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Room() {}

    public Room(String code) {
        this.code = code;
    }

    public void startReading(Instant endsAt) {
        this.status = RoomStatus.READING;
        this.readingEndsAt = endsAt;
    }

    public void startPlaying() {
        this.status = RoomStatus.PLAYING;
    }

    public void setClockValue(int clockValue) {
        this.clockValue = clockValue;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public RoomStatus getStatus() { return status; }
    public int getClockValue() { return clockValue; }
    public Instant getReadingEndsAt() { return readingEndsAt; }
}
