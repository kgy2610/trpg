package com.example.trpg.service;

import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import com.example.trpg.domain.RoomStatus;
import com.example.trpg.dto.EndingDtos.EndingResult;
import com.example.trpg.dto.EndingDtos.EndingState;
import com.example.trpg.dto.GameDtos.AssignmentInfo;
import com.example.trpg.exception.ApiException;
import com.example.trpg.game.EndingContent;
import com.example.trpg.game.EndingContent.Ending;
import com.example.trpg.game.GameCharacter;
import com.example.trpg.game.GameContent;
import com.example.trpg.game.SecretRole;
import com.example.trpg.repository.PlayerRepository;
import com.example.trpg.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EndingService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GameResetService gameResetService;

    public EndingService(RoomRepository roomRepository,
                        PlayerRepository playerRepository,
                        GameResetService gameResetService) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.gameResetService = gameResetService;
    }

    // GM이 결말 조건을 설정. 게임 진행 중에만 가능
    @Transactional
    public void setConditions(String code, String token, boolean engineStopped, boolean docsSecured) {
        Room room = lockRoom(code);
        requireGm(room, token);
        if (room.getStatus() != RoomStatus.PLAYING) {
            throw new ApiException(HttpStatus.CONFLICT, "게임 진행 중에만 결말 조건을 바꿀 수 있습니다.");
        }
        room.setEndingConditions(engineStopped, docsSecured);   // 트랜잭션이 끝날 때 자동으로 DB에 반영됨
    }

    // GM 전용: 지금 조건과 "이 조건이면 어떤 결말인지" 미리 보기
    @Transactional(readOnly = true)
    public EndingState getState(String code, String token) {
        Room room = findRoom(code);
        requireGm(room, token);
        Ending preview = EndingContent.decide(room.isEngineStopped(), room.isDocsSecured());
        return new EndingState(room.isEngineStopped(), room.isDocsSecured(), preview.key(), preview.title());
    }

    // GM이 결말 진행. 조건으로 결말을 정하고 종료 상태로 전환. 반환값: 방송에 쓸 방 코드
    @Transactional
    public String finish(String code, String token) {
        Room room = lockRoom(code);
        requireGm(room, token);
        if (room.getStatus() != RoomStatus.PLAYING) {
            throw new ApiException(HttpStatus.CONFLICT, "게임 진행 중에만 결말을 진행할 수 있습니다.");
        }
        Ending ending = EndingContent.decide(room.isEngineStopped(), room.isDocsSecured());
        room.finish(ending.key());
        return room.getCode();
    }

    // 종료 후 결과: 결말, 후일담, 정체 공개. 종료 상태일 때만 이 방의 참가자에게 돌려줌
    @Transactional(readOnly = true)
    public EndingResult getResult(String code, String token) {
        Room room = findRoom(code);
        requirePlayer(room, token);

        // 서버가 보장하는 비밀 유지: 게임이 끝나기 전에는 정체 공개 정보를 어떤 경우에도 내보내지 않음
        if (room.getStatus() != RoomStatus.ENDED) {
            throw new ApiException(HttpStatus.CONFLICT, "게임이 아직 끝나지 않았습니다.");
        }

        Ending ending = EndingContent.decide(room.isEngineStopped(), room.isDocsSecured());

        List<AssignmentInfo> assignments = playerRepository.findByRoomOrderByIdAsc(room).stream()
                .filter(p -> !p.isGm() && p.getCharacterKey() != null && p.getSecretRole() != null)
                .map(p -> new AssignmentInfo(
                        p.getNickname(),
                        GameContent.character(GameCharacter.valueOf(p.getCharacterKey())).name(),
                        GameContent.secret(SecretRole.valueOf(p.getSecretRole())).title()))
                .toList();

        String stowaway = playerRepository.findByRoomOrderByIdAsc(room).stream()
                .filter(p -> SecretRole.STOWAWAY.name().equals(p.getSecretRole()))
                .map(Player::getNickname)
                .findFirst()
                .orElse("");

        return new EndingResult(ending.key(), ending.title(), ending.epilogue(), stowaway, assignments);
    }

    // GM이 새 판 시작: 종료 상태에서만 대기실로 되돌림. 반환값: 방송에 쓸 방 코드
    @Transactional
    public String restart(String code, String token) {
        Room room = lockRoom(code);
        requireGm(room, token);
        if (room.getStatus() != RoomStatus.ENDED) {
            throw new ApiException(HttpStatus.CONFLICT, "게임이 끝난 뒤에만 새 판을 시작할 수 있습니다.");
        }
        gameResetService.resetRoom(room);
        return room.getCode();
    }

    private Room findRoom(String code) {
        return roomRepository.findByCode(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));
    }

    private Room lockRoom(String code) {
        return roomRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));
    }

    // 토큰으로 요청자를 확인하고, 이 방의 참가자인지 검사
    private Player requirePlayer(Room room, String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
        }
        Player player = playerRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."));
        if (!player.getRoom().getId().equals(room.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "이 방의 참가자가 아닙니다.");
        }
        return player;
    }

    // 서버가 최종 방어선: 화면에서 버튼을 숨겨도 요청을 직접 보내면 우회 가능
    private void requireGm(Room room, String token) {
        Player player = requirePlayer(room, token);
        if (!player.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 할 수 있습니다.");
        }
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}