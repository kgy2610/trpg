package com.example.trpg.service;

import com.example.trpg.domain.ClueReveal;
import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import com.example.trpg.domain.RoomStatus;
import com.example.trpg.dto.ClueDtos.ClueView;
import com.example.trpg.dto.ClueDtos.GmClueView;
import com.example.trpg.exception.ApiException;
import com.example.trpg.game.ClueContent;
import com.example.trpg.repository.ClueRevealRepository;
import com.example.trpg.repository.PlayerRepository;
import com.example.trpg.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ClueService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final ClueRevealRepository clueRevealRepository;
    private final EventLogService eventLogService;

    public ClueService(RoomRepository roomRepository,
                        PlayerRepository playerRepository,
                        ClueRevealRepository clueRevealRepository,
                        EventLogService eventLogService) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.clueRevealRepository = clueRevealRepository;
        this.eventLogService = eventLogService;
    }

    // 방송용: 방 코드로 공개된 단서를 조회 (서버 내부에서만 호출되고, 요청자 확인은 호출 전에 끝나 있음)
    @Transactional(readOnly = true)
    public List<ClueView> listRevealed(String roomCode) {
        return revealedViews(findRoom(roomCode));
    }

    // 플레이어/GM이 새로고침할 때 쓰는 조회. 이 방의 참가자만 볼 수 있음
    @Transactional(readOnly = true)
    public List<ClueView> getRevealedForPlayer(String code, String token) {
        Room room = findRoom(code);
        requirePlayer(room, token);
        return revealedViews(room);
    }

    // GM 전용: 공개 전 단서의 내용까지 포함한 전체 목록
    @Transactional(readOnly = true)
    public List<GmClueView> getAllForGm(String code, String token) {
        Room room = findRoom(code);
        Player me = requirePlayer(room, token);
        if (!me.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 볼 수 있습니다.");
        }

        Set<String> revealedKeys = clueRevealRepository.findByRoomOrderByIdAsc(room).stream()
                .map(ClueReveal::getClueKey)
                .collect(Collectors.toSet());

        return ClueContent.all().stream()
                .map(c -> new GmClueView(c.key(), c.location(), c.title(), c.content(), revealedKeys.contains(c.key())))
                .toList();
    }

    // GM이 단서를 공개(revealed=true)하거나 숨김(false). 같은 요청을 여러 번 보내도 결과가 같음. 반환값: 방송에 쓸 방 코드
    @Transactional
    public String setRevealed(String code, String token, String key, boolean revealed) {
        Room room = lockRoom(code);
        Player me = requirePlayer(room, token);

        // 서버가 최종 방어선: 화면에서 버튼을 숨겨도 요청을 직접 보내면 우회 가능
        if (!me.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 단서를 공개할 수 있습니다.");
        }
        if (room.getStatus() != RoomStatus.PLAYING) {
            throw new ApiException(HttpStatus.CONFLICT, "게임 진행 중에만 단서를 공개할 수 있습니다.");
        }
        if (key == null || ClueContent.find(key).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 단서입니다.");
        }

        Optional<ClueReveal> existing = clueRevealRepository.findByRoomAndClueKey(room, key);
        if (revealed) {
            if (existing.isEmpty()) {
                clueRevealRepository.save(new ClueReveal(room, key));
            eventLogService.recordClueReveal(room, key, ClueContent.find(key).get().title());
            }
        } else {
            existing.ifPresent(clueRevealRepository::delete);
        }
        return room.getCode();
    }

    // 공개된 단서를 공개한 순서대로 응답 형식으로 변환
    private List<ClueView> revealedViews(Room room) {
        List<ClueView> result = new ArrayList<>();
        for (ClueReveal reveal : clueRevealRepository.findByRoomOrderByIdAsc(room)) {
            ClueContent.find(reveal.getClueKey()).ifPresent(c ->
                    result.add(new ClueView(c.key(), c.location(), c.title(), c.content())));
        }
        return result;
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