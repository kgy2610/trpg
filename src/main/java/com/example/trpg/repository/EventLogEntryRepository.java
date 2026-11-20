package com.example.trpg.repository;

import com.example.trpg.domain.EventLogEntry;
import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventLogEntryRepository extends JpaRepository<EventLogEntry, Long> {

    // 화면에 보여줄 최근 기록 200개
    List<EventLogEntry> findTop200ByRoomAndDeletedFalseOrderByIdDesc(Room room);

    // 자동 기록 중복 검사.
    boolean existsByRoomAndAutoKey(Room room, String autoKey);

    // 최소 간격 검사: 이 사람이 마지막으로 쓴 기록
    Optional<EventLogEntry> findFirstBySenderOrderByIdDesc(Player sender);

    // 게임 초기화: 방의 기록을 모두 삭제
    void deleteByRoom(Room room);
}