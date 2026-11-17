package com.example.trpg.repository;

import com.example.trpg.domain.Room;
import com.example.trpg.domain.RoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByCode(String code);

    boolean existsByCode(String code);

    // 입장 시 동시 접속으로 인원이 초과되는 것을 막기 위해 방 행을 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.code = :code")
    Optional<Room> findByCodeForUpdate(@Param("code") String code);

    // 특정 상태이면서 종료 시각이 지난 방 (읽기 시간이 끝난 방 찾기)
    List<Room> findByStatusAndReadingEndsAtBefore(RoomStatus status, Instant time);
}