package com.example.trpg.game;

import java.util.Optional;

public enum Stat {
    STRENGTH("힘"),
    MIND("지능"),
    CHARM("매력");

    private final String label;

    Stat(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    // 요청에서 온 문자열을 능력치로 변환 (알 수 없는 값이면 비어 있음)
    public static Optional<Stat> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (Stat stat : values()) {
            if (stat.name().equals(name.trim().toUpperCase())) {
                return Optional.of(stat);
            }
        }
        return Optional.empty();
    }
}