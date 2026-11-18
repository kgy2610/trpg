package com.example.trpg.service;

import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import com.example.trpg.domain.RoomStatus;
import com.example.trpg.dto.GameDtos.AssignmentInfo;
import com.example.trpg.dto.GameDtos.CharacterCard;
import com.example.trpg.dto.GameDtos.MyRoleResponse;
import com.example.trpg.dto.GameDtos.SecretCard;
import com.example.trpg.exception.ApiException;
import com.example.trpg.game.GameCharacter;
import com.example.trpg.game.GameContent;
import com.example.trpg.game.SecretRole;
import com.example.trpg.repository.PlayerRepository;
import com.example.trpg.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class GameService {

    // 플레이어 수 (GM 제외).
    private static final int REQUIRED_PLAYERS = 5;
    // 읽기 시간 (초)
    private static final long READING_SECONDS = 300;

    private final SecureRandom random = new SecureRandom();
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final PresenceService presenceService;

    public GameService(RoomRepository roomRepository,
                        PlayerRepository playerRepository,
                        PresenceService presenceService) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.presenceService = presenceService;
    }

    // 한 명에게 보낼 내용 (대상 playerId + 그 사람 전용 응답)
    public record RoleDelivery(Long playerId, MyRoleResponse payload) {}

    // 게임 시작. 반환값: 방송에 쓸 방 코드
    @Transactional
    public String startGame(String code, String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
        }

        // 입장/나가기/강퇴와 같은 방식으로 방 행을 먼저 잠가서, 시작 직전의 입장/나가기와 순서대로 처리됨
        Room room = roomRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));

        Player requester = playerRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."));

        if (!requester.getRoom().getId().equals(room.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "이 방의 참가자가 아닙니다.");
        }
        // 서버가 최종 방어선: 화면에서 버튼을 숨겨도 요청을 직접 보내면 우회 가능
        if (!requester.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 게임을 시작할 수 있습니다.");
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 시작된 게임입니다.");
        }

        List<Player> players = playerRepository.findByRoomOrderByIdAsc(room).stream()
                .filter(p -> !p.isGm())
                .toList();

        if (players.size() != REQUIRED_PLAYERS) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "플레이어 " + REQUIRED_PLAYERS + "명이 모두 입장해야 시작할 수 있습니다. (현재 " + players.size() + "명)");
        }

        // 오프라인인 사람은 카드를 받을 수 없으므로 전원 접속을 요구
        List<String> offline = players.stream()
                .filter(p -> !presenceService.isOnline(p.getId()))
                .map(Player::getNickname)
                .toList();
        if (!offline.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "접속이 끊긴 참가자가 있습니다: " + String.join(", ", offline));
        }

        assignRoles(players);
        room.startReading(Instant.now().plusSeconds(READING_SECONDS));
        return room.getCode();
    }

    @Transactional
    public String markReady(String code, String token) {
        Room room = lockRoom(code);
        Player me = requirePlayer(room, token);

        if (me.isGm()) {
            throw new ApiException(HttpStatus.CONFLICT, "GM은 읽었어요를 누를 수 없습니다.");
        }
        if (room.getStatus() != RoomStatus.READING) {
            throw new ApiException(HttpStatus.CONFLICT, "읽기 단계가 아닙니다.");
        }

        me.markReady();   // 이미 눌렀어도 결과는 같음 (여러 번 눌러도 안전)

        List<Player> players = playerRepository.findByRoomOrderByIdAsc(room).stream()
                .filter(p -> !p.isGm())
                .toList();
        boolean allReady = !players.isEmpty() && players.stream().allMatch(Player::isReady);
        if (allReady) {
            room.startPlaying();
        }
        return room.getCode();
    }

    // GM이 읽기 시간을 건너뛰고 바로 시작. 반환값: 방송에 쓸 방 코드
    @Transactional
    public String beginPlaying(String code, String token) {
        Room room = lockRoom(code);
        Player me = requirePlayer(room, token);

        // 서버가 최종 방어선: 화면에서 버튼을 숨겨도 요청을 직접 보내면 우회 가능
        if (!me.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 바로 시작할 수 있습니다.");
        }
        if (room.getStatus() != RoomStatus.READING) {
            throw new ApiException(HttpStatus.CONFLICT, "읽기 단계에서만 바로 시작할 수 있습니다.");
        }

        room.startPlaying();
        return room.getCode();
    }

    @Transactional
    public String setClock(String code, String token, int value) {
        Room room = lockRoom(code);
        Player me = requirePlayer(room, token);

        // 서버가 최종 방어선: 화면에서 버튼을 숨겨도 요청을 직접 보내면 우회 가능
        if (!me.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 시계를 조작할 수 있습니다.");
        }
        if (room.getStatus() != RoomStatus.PLAYING) {
            throw new ApiException(HttpStatus.CONFLICT, "게임 진행 중에만 시계를 조작할 수 있습니다.");
        }
        if (value < 0 || value > GameContent.CLOCK_MAX) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "시계는 0~" + GameContent.CLOCK_MAX + "칸 사이로만 설정할 수 있습니다.");
        }

        room.setClockValue(value);   // 트랜잭션이 끝날 때 자동으로 DB에 반영됨
        return room.getCode();
    }

    // GM 전용: 칸별 사건 메모
    @Transactional(readOnly = true)
    public List<String> getClockEvents(String code, String token) {
        Room room = roomRepository.findByCode(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));
        Player me = requirePlayer(room, token);
        if (!me.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 볼 수 있습니다.");
        }
        return GameContent.clockEvents();
    }

    // 읽기 시간이 끝난 방들을 게임 중으로 전환 (스케줄러가 호출). 반환값: 전환된 방 코드들
    @Transactional
    public List<String> advanceExpiredRooms() {
        Instant now = Instant.now();
        List<String> advanced = new ArrayList<>();

        for (Room candidate : roomRepository.findByStatusAndReadingEndsAtBefore(RoomStatus.READING, now)) {
            // 읽었어요/바로 시작 요청과 겹치지 않도록 방을 잠근 뒤 상태를 다시 확인
            roomRepository.findByCodeForUpdate(candidate.getCode()).ifPresent(room -> {
                if (room.getStatus() == RoomStatus.READING
                        && room.getReadingEndsAt() != null
                        && room.getReadingEndsAt().isBefore(now)) {
                    room.startPlaying();
                    advanced.add(room.getCode());
                }
            });
        }
        return advanced;
    }

    // 방을 잠그고 가져옴
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

    // 내 역할 조회 (새로고침, 재접속 때 사용). 플레이어는 본인 카드, GM은 전체 배정표
    @Transactional(readOnly = true)
    public MyRoleResponse getMyRole(String code, String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
        }
        Room room = roomRepository.findByCode(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));
        Player me = playerRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."));

        // 다른 방의 토큰으로 이 방의 정보를 읽는 것을 막음
        if (!me.getRoom().getId().equals(room.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "이 방의 참가자가 아닙니다.");
        }
        if (room.getStatus() == RoomStatus.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, "게임이 아직 시작되지 않았습니다.");
        }

        List<Player> all = playerRepository.findByRoomOrderByIdAsc(room);
        return buildRoleResponse(room, me, all);
    }

    // 게임 시작 직후, 방 전원에게 각자의 응답을 만들어 돌려줌 (전송은 리스너가 담당)
    @Transactional(readOnly = true)
    public List<RoleDelivery> loadRoleDeliveries(String roomCode) {
        Room room = roomRepository.findByCode(normalize(roomCode))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));
        List<Player> all = playerRepository.findByRoomOrderByIdAsc(room);
        return all.stream()
                .map(p -> new RoleDelivery(p.getId(), buildRoleResponse(room, p, all)))
                .toList();
    }

    // 요청한 사람 한 명에게 필요한 정보만 담아서 응답을 만듦 (다른 사람의 카드는 절대 포함하지 않음)
    private MyRoleResponse buildRoleResponse(Room room, Player me, List<Player> all) {
        Long endsAt = room.getReadingEndsAt() == null ? null : room.getReadingEndsAt().toEpochMilli();
        long now = System.currentTimeMillis();
        String status = room.getStatus().name();

        if (me.isGm()) {
            List<AssignmentInfo> assignments = all.stream()
                    .filter(p -> !p.isGm())
                    .map(p -> new AssignmentInfo(
                            p.getNickname(),
                            GameContent.displayName(p),
                            GameContent.secret(SecretRole.valueOf(p.getSecretRole())).title()))
                    .toList();
            return new MyRoleResponse(true, null, null, assignments, status, endsAt, now);
        }

        if (me.getCharacterKey() == null || me.getSecretRole() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "역할이 아직 배정되지 않았습니다.");
        }
        CharacterCard character = GameContent.character(GameCharacter.valueOf(me.getCharacterKey()))
                .withName(GameContent.displayName(me));
        SecretCard secret = GameContent.secret(SecretRole.valueOf(me.getSecretRole()));
        return new MyRoleResponse(false, character, secret, null, status, endsAt, now);
    }

    // 캐릭터와 비밀 카드를 "각각 독립적으로" 섞어서 배정
    private void assignRoles(List<Player> players) {
        int n = players.size();

        List<GameCharacter> characters = new ArrayList<>(List.of(GameCharacter.values()));
        Collections.shuffle(characters, random);

        // 밀항자 1명 + 나머지 승객
        List<SecretRole> roles = new ArrayList<>();
        roles.add(SecretRole.STOWAWAY);
        for (int i = 1; i < n; i++) {
            roles.add(SecretRole.PASSENGER);
        }
        Collections.shuffle(roles, random);

        List<String> surnames = new ArrayList<>(GameContent.SURNAMES);
        Collections.shuffle(surnames, random);

        for (int i = 0; i < n; i++) {
            players.get(i).assign(characters.get(i).name(), roles.get(i).name(), surnames.get(i));
        }
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}