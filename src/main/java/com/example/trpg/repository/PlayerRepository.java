package com.example.trpg.repository;

import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;          // [추가]
import org.springframework.data.repository.query.Param;        // [추가]

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByRoomOrderByIdAsc(Room room);

    // GM이 아닌 플레이어 수
    long countByRoomAndGmFalse(Room room);

    boolean existsByRoomAndNickname(Room room, String nickname);

    Optional<Player> findByRoomAndNickname(Room room, String nickname);

    Optional<Player> findByToken(String token);

    // 방 정보까지 한 번에 조회
    @Query("select p from Player p join fetch p.room where p.token = :token")
    Optional<Player> findByTokenWithRoom(@Param("token") String token);
}