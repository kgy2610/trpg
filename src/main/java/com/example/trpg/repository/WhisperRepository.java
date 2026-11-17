package com.example.trpg.repository;

import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import com.example.trpg.domain.Whisper;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WhisperRepository extends JpaRepository<Whisper, Long> {

    // 한 플레이어가 받은 귓속말 (최신순, 최대 50개)
    List<Whisper> findTop50ByRecipientOrderByIdDesc(Player recipient);

    // GM이 방에서 보낸 전체 기록 (최신순, 최대 100개)
    List<Whisper> findTop100ByRoomOrderByIdDesc(Room room);

    // 방의 귓속말을 모두 삭제 (게임 초기화용)
    void deleteByRoom(Room room);
}