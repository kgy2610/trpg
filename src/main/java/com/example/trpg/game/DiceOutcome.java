package com.example.trpg.game;

public enum DiceOutcome {
    SUCCESS("성공"),
    PARTIAL("대가를 치르는 성공"),
    FAILURE("실패");

    private final String label;

    DiceOutcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    // 10 이상 성공, 7~9 대가를 치르는 성공, 6 이하 실패
    public static DiceOutcome of(int total) {
        if (total >= 10) {
            return SUCCESS;
        }
        if (total >= 7) {
            return PARTIAL;
        }
        return FAILURE;
    }
}