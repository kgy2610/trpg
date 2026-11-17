package com.example.trpg.service;

import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import com.example.trpg.domain.RoomStatus;
import com.example.trpg.exception.ApiException;
import com.example.trpg.repository.PlayerRepository;
import com.example.trpg.repository.RoomRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local")   // 개발 환경(local)에서만 이 클래스가 만들어짐. 다른 환경에서는 아예 존재하지 않음
public class DevResetService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GameResetService gameResetService;   // [수정] 저장소 여러 개 대신 공통 서비스 하나

    public DevResetService(RoomRepository roomRepository,
                           PlayerRepository playerRepository,
                           GameResetService gameResetService) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.gameResetService = gameResetService;
    }

    // 게임을 강제로 중단하고 대기실 상태로 되돌림. 반환값: 방송에 쓸 방 코드
    @Transactional
    public String resetGame(String code, String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
        }

        // 다른 요청과 겹치지 않도록 방을 먼저 잠금
        Room room = roomRepository.findByCodeForUpdate(code == null ? "" : code.trim().toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));

        Player me = playerRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."));
        if (!me.getRoom().getId().equals(room.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "이 방의 참가자가 아닙니다.");
        }
        // 서버가 최종 방어선: 버튼을 숨겨도 요청을 직접 보내면 우회 가능
        if (!me.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 게임을 중단할 수 있습니다.");
        }
        if (room.getStatus() == RoomStatus.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 대기 중입니다.");
        }

        gameResetService.resetRoom(room);   // [수정] 초기화 본체는 공통 서비스가 처리
        return room.getCode();
    }
}