package com.example.trpg.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dice_roll")
public class DiceRoll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    // 어떤 능력치로 굴렸는지 (Stat의 이름)
    @Column(nullable = false, length = 20)
    private String stat;

    private int die1;
    private int die2;
    private int modifier;
    private int total;

    // 판정 결과 (DiceOutcome의 이름)
    @Column(nullable = false, length = 20)
    private String outcome;

    // 행동 설명 (선택)
    @Column(length = 60)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected DiceRoll() {}

    public DiceRoll(Room room, Player player, String stat, int die1, int die2,
                    int modifier, int total, String outcome, String reason) {
        this.room = room;
        this.player = player;
        this.stat = stat;
        this.die1 = die1;
        this.die2 = die2;
        this.modifier = modifier;
        this.total = total;
        this.outcome = outcome;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public Player getPlayer() { return player; }
    public String getStat() { return stat; }
    public int getDie1() { return die1; }
    public int getDie2() { return die2; }
    public int getModifier() { return modifier; }
    public int getTotal() { return total; }
    public String getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}