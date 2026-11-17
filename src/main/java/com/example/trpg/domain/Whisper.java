package com.example.trpg.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "whisper")
public class Whisper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    // 받는 사람 (보내는 사람은 항상 GM이라 따로 저장하지 않음)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id")
    private Player recipient;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Whisper() {}

    public Whisper(Room room, Player recipient, String message) {
        this.room = room;
        this.recipient = recipient;
        this.message = message;
    }

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public Player getRecipient() { return recipient; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}