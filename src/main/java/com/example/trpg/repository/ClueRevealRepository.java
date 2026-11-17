package com.example.trpg.repository;

import com.example.trpg.domain.ClueReveal;
import com.example.trpg.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClueRevealRepository extends JpaRepository<ClueReveal, Long> {

    // 공개된 순서대로
    List<ClueReveal> findByRoomOrderByIdAsc(Room room);

    Optional<ClueReveal> findByRoomAndClueKey(Room room, String clueKey);

    // 개발용: 방의 공개 기록을 모두 삭제 (게임 초기화용)
    void deleteByRoom(Room room);
}