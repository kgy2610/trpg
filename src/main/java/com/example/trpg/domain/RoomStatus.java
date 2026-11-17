package com.example.trpg.domain;

public enum RoomStatus {
    WAITING,    //입장대기
    READING,    //역할 배정 + 읽기
    PLAYING,    //본 게임 진행
    ENDED       //종료
}
