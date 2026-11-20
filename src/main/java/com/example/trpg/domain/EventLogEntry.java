package com.example.trpg.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "event_log_entry",
    // 자동 기록은 같은 키로 두 번 저장될 수 없음
    uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "auto_key"})
)
public class EventLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EventKind kind;

    // 보낸 사람
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private Player sender;

    // 화면에 표시할 이름: NPC 이름 또는 캐릭터 이름
    @Column(length = 40)
    private String speaker;

    @Column(nullable = false, length = 200)
    private String content;

    // 자동 기록의 중복 방지 키
    @Column(name = "auto_key", length = 60)
    private String autoKey;

    // GM이 지운 기록. 행은 남겨 두어서 같은 자동 기록이 다시 생기지 않게 함
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected EventLogEntry() {}

    public EventLogEntry(Room room, EventKind kind, Player sender, String speaker, String content, String autoKey) {
        this.room = room;
        this.kind = kind;
        this.sender = sender;
        this.speaker = speaker;
        this.content = content;
        this.autoKey = autoKey;
    }

    public void markDeleted() {
        this.deleted = true;
    }

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public EventKind getKind() { return kind; }
    public Player getSender() { return sender; }
    public String getSpeaker() { return speaker; }
    public String getContent() { return content; }
    public String getAutoKey() { return autoKey; }
    public boolean isDeleted() { return deleted; }
    public Instant getCreatedAt() { return createdAt; }
}