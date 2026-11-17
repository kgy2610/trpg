package com.example.trpg.service;

import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import com.example.trpg.domain.RoomStatus;
import com.example.trpg.domain.Whisper;
import com.example.trpg.dto.WhisperDtos.GmWhisperView;
import com.example.trpg.dto.WhisperDtos.WhisperView;
import com.example.trpg.exception.ApiException;
import com.example.trpg.game.GameCharacter;
import com.example.trpg.game.GameContent;
import com.example.trpg.repository.PlayerRepository;
import com.example.trpg.repository.RoomRepository;
import com.example.trpg.repository.WhisperRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WhisperService {

    private static final int MAX_LENGTH = 200;

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final WhisperRepository whisperRepository;

    public WhisperService(RoomRepository roomRepository,
                          PlayerRepository playerRepository,
                          WhisperRepository whisperRepository) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.whisperRepository = whisperRepository;
    }

    // 전송 결과: 방송할 방 코드와, 개인 채널로 보낼 두 사람(받는 사람, GM)
    public record SendResult(String roomCode, Long recipientPlayerId, Long gmPlayerId) {}

    // GM이 플레이어 한 명에게 귓속말을 보냄
    @Transactional
    public SendResult send(String code, String token, String nickname, String text) {
        Room room = lockRoom(code);
        Player me = requirePlayer(room, token);

        // 서버가 최종 방어선: 화면에서 버튼을 숨겨도 요청을 직접 보내면 우회 가능
        if (!me.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 귓속말을 보낼 수 있습니다.");
        }
        if (room.getStatus() != RoomStatus.PLAYING) {
            throw new ApiException(HttpStatus.CONFLICT, "게임 진행 중에만 귓속말을 보낼 수 있습니다.");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "받는 사람의 닉네임이 필요합니다.");
        }

        Player target = playerRepository.findByRoomAndNickname(room, nickname.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "해당 닉네임의 참가자가 없습니다."));
        if (target.isGm()) {
            throw new ApiException(HttpStatus.CONFLICT, "GM에게는 귓속말을 보낼 수 없습니다.");
        }

        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "내용을 입력해 주세요.");
        }
        if (clean.length() > MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "귓속말은 " + MAX_LENGTH + "자 이하로 써 주세요.");
        }

        whisperRepository.save(new Whisper(room, target, clean));
        return new SendResult(room.getCode(), target.getId(), me.getId());
    }

    // 받는 사람 전용 전송용: 이 플레이어가 받은 귓속말 (최신순). 서버 내부에서만 호출
    @Transactional(readOnly = true)
    public List<WhisperView> listForRecipient(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "참가자를 찾을 수 없습니다."));
        return whisperRepository.findTop50ByRecipientOrderByIdDesc(player).stream()
                .map(w -> new WhisperView(w.getId(), w.getMessage(), w.getCreatedAt().toEpochMilli()))
                .toList();
    }

    // GM 전용 전송용: 방에서 보낸 전체 기록 (최신순). 서버 내부에서만 호출
    @Transactional(readOnly = true)
    public List<GmWhisperView> listAllForGm(String roomCode) {
        Room room = findRoom(roomCode);
        return whisperRepository.findTop100ByRoomOrderByIdDesc(room).stream()
                .map(w -> {
                    Player to = w.getRecipient();
                    String character = to.getCharacterKey() == null
                            ? ""
                            : GameContent.character(GameCharacter.valueOf(to.getCharacterKey())).name();
                    return new GmWhisperView(w.getId(), to.getNickname(), character,
                            w.getMessage(), w.getCreatedAt().toEpochMilli());
                })
                .toList();
    }

    // 새로고침 때 쓰는 조회: 내가 받은 귓속말만. GM은 받는 쪽이 아니라서 빈 목록
    @Transactional(readOnly = true)
    public List<WhisperView> getMine(String code, String token) {
        Room room = findRoom(code);
        Player me = requirePlayer(room, token);
        if (me.isGm()) {
            return List.of();
        }
        return listForRecipient(me.getId());
    }

    // GM 전용 조회: 보낸 전체 기록
    @Transactional(readOnly = true)
    public List<GmWhisperView> getAllForGm(String code, String token) {
        Room room = findRoom(code);
        Player me = requirePlayer(room, token);
        if (!me.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 볼 수 있습니다.");
        }
        return listAllForGm(room.getCode());
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

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}