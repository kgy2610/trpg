package com.example.trpg.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "clue_reveal",
    uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "clue_key"})
)
public class ClueReveal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "clue_key", nullable = false, length = 40)
    private String clueKey;

    @Column(nullable = false, updatable = false)
    private Instant revealedAt = Instant.now();

    protected ClueReveal() {}

    public ClueReveal(Room room, String clueKey) {
        this.room = room;
        this.clueKey = clueKey;
    }

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public String getClueKey() { return clueKey; }
    public Instant getRevealedAt() { return revealedAt; }
}