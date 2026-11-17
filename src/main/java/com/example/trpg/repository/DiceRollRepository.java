package com.example.trpg.repository;

import com.example.trpg.domain.DiceRoll;
import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiceRollRepository extends JpaRepository<DiceRoll, Long> {

    // 방의 최근 판정 20개 (최신순)
    List<DiceRoll> findTop20ByRoomOrderByIdDesc(Room room);

    // 쿨다운 검사용: 이 플레이어의 가장 최근 판정
    Optional<DiceRoll> findFirstByPlayerOrderByIdDesc(Player player);

    // 개발용: 방의 판정 기록을 모두 삭제 (게임 초기화용)
    void deleteByRoom(Room room);
}