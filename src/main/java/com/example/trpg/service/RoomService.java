package com.example.trpg.service;

import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import com.example.trpg.domain.RoomStatus;
import com.example.trpg.dto.RoomDtos.JoinResponse;
import com.example.trpg.dto.RoomDtos.PlayerInfo;
import com.example.trpg.dto.RoomDtos.RoomInfo;
import com.example.trpg.exception.ApiException;
import com.example.trpg.repository.PlayerRepository;
import com.example.trpg.repository.RoomRepository;
import com.example.trpg.service.PresenceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RoomService {

    // 헷갈리는 문자(0/O, 1/I) 제외
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_PLAYERS = 5;   // GM 제외
    private static final int MAX_NICKNAME_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final PresenceService presenceService;

    public RoomService(RoomRepository roomRepository,
                        PlayerRepository playerRepository,
                        PresenceService presenceService) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.presenceService = presenceService;
    }

    @Transactional
    public JoinResponse createRoom(String nickname) {
        String name = validateNickname(nickname);
        Room room = roomRepository.save(new Room(generateUniqueCode()));
        Player gm = playerRepository.save(new Player(room, name, true, newToken()));
        return new JoinResponse(room.getCode(), gm.getNickname(), true, gm.getToken());
    }

    @Transactional
    public JoinResponse joinRoom(String code, String nickname) {
        String name = validateNickname(nickname);

        // 방 행을 잠가서 동시에 입장해도 인원 검사가 정확하게 동작
        Room room = roomRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));

        // "대기 중이 아니면 무조건 거부"에서 "종료된 방만 거부"로 완화
        if (room.getStatus() == RoomStatus.ENDED) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 종료된 방입니다.");
        }

        // 같은 닉네임이 이미 있는 경우: 자리 인수 가능 여부를 판단
        Optional<Player> existing = playerRepository.findByRoomAndNickname(room, name);
        if (existing.isPresent()) {
            Player owner = existing.get();

            // GM 자리, 그리고 접속 중인 자리는 항상 인수 불가
            if (owner.isGm() || presenceService.isOnline(owner.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
            }
            // 게임 중에는 GM이 재접속을 허용한 경우에만 인수 가능
            boolean waiting = room.getStatus() == RoomStatus.WAITING;
            if (!waiting && !owner.isRejoinAllowed()) {
                throw new ApiException(HttpStatus.CONFLICT, "게임 진행 중에는 GM이 재접속을 허용해야 합니다.");
            }

            owner.changeToken(newToken());  // 옛 토큰은 무효가 됨
            owner.setRejoinAllowed(false);
            return new JoinResponse(room.getCode(), owner.getNickname(), false, owner.getToken());
        }

        // 새로 입장하는 경우: 대기 중일 때만, 인원 검사는 새 참가자에게만 적용
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 게임이 시작된 방입니다.");
        }
        if (playerRepository.countByRoomAndGmFalse(room) >= MAX_PLAYERS) {
            throw new ApiException(HttpStatus.CONFLICT, "방이 가득 찼습니다. (플레이어 " + MAX_PLAYERS + "명)");
        }

        Player player = playerRepository.save(new Player(room, name, false, newToken()));
        return new JoinResponse(room.getCode(), player.getNickname(), false, player.getToken());
    }

    @Transactional(readOnly = true)
    public RoomInfo getRoom(String code) {
        Room room = roomRepository.findByCode(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));

        List<PlayerInfo> players = playerRepository.findByRoomOrderByIdAsc(room).stream()
                .map(p -> new PlayerInfo(p.getNickname(), p.isGm(), presenceService.isOnline(p.getId()),
                        p.isRejoinAllowed(), p.isReady()))
                .toList();

        return new RoomInfo(room.getCode(), room.getStatus().name(), MAX_PLAYERS, players, room.getClockValue());
    }

    @Transactional
    public String leaveRoom(String code, String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
        }

        // 입장 처리와 같은 방식으로 방 행을 먼저 잠가서, 입장/나가기가 동시에 와도 순서대로 처리됨
        Room room = roomRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));

        Player player = playerRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."));

        // 다른 방의 참가자가 이 방에서 나가는 요청을 보내는 것을 막음
        if (!player.getRoom().getId().equals(room.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "이 방의 참가자가 아닙니다.");
        }
        if (player.isGm()) {
            throw new ApiException(HttpStatus.CONFLICT, "GM은 대기실을 나갈 수 없습니다.");
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, "게임이 시작되어 나갈 수 없습니다.");
        }

        playerRepository.delete(player);
        return room.getCode();   // 방송에 쓸 정식 방 코드(대문자)를 돌려줌
    }

    @Transactional
    public String kickPlayer(String code, String token, String targetNickname) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
        }
        if (targetNickname == null || targetNickname.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "내보낼 참가자의 닉네임이 필요합니다.");
        }

        Room room = roomRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));

        Player requester = playerRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."));

        if (!requester.getRoom().getId().equals(room.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "이 방의 참가자가 아닙니다.");
        }
        // GM 여부 확인
        if (!requester.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 참가자를 내보낼 수 있습니다.");
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, "게임이 시작된 뒤에는 내보낼 수 없습니다.");
        }

        Player target = playerRepository.findByRoomAndNickname(room, targetNickname.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "해당 닉네임의 참가자가 없습니다."));
        if (target.isGm()) {
            throw new ApiException(HttpStatus.CONFLICT, "GM은 내보낼 수 없습니다.");
        }
        // 오프라인인 경우에만 강퇴 허용
        if (presenceService.isOnline(target.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "접속 중인 참가자는 내보낼 수 없습니다. 본인이 직접 나가야 합니다.");
        }

        playerRepository.delete(target);
        return room.getCode();
    }

    @Transactional
    public String setRejoinPermission(String code, String token, String targetNickname, boolean allowed) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
        }
        if (targetNickname == null || targetNickname.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "대상 참가자의 닉네임이 필요합니다.");
        }

        Room room = roomRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));

        Player requester = playerRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."));

        if (!requester.getRoom().getId().equals(room.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "이 방의 참가자가 아닙니다.");
        }
        if (!requester.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 재접속을 허용할 수 있습니다.");
        }
        // 대기 중에는 허용이 필요 없고, 남겨 두면 게임 시작 뒤에 오래된 허용이 살아 있게 되므로 막음
        if (room.getStatus() == RoomStatus.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, "대기 중에는 허용 없이 같은 닉네임으로 다시 입장할 수 있습니다.");
        }
        if (room.getStatus() == RoomStatus.ENDED) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 종료된 방입니다.");
        }

        Player target = playerRepository.findByRoomAndNickname(room, targetNickname.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "해당 닉네임의 참가자가 없습니다."));
        if (target.isGm()) {
            throw new ApiException(HttpStatus.CONFLICT, "GM에게는 사용할 수 없습니다.");
        }
        if (allowed && presenceService.isOnline(target.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 접속 중인 참가자입니다.");
        }

        target.setRejoinAllowed(allowed);   // 트랜잭션이 끝날 때 자동으로 DB에 반영됨
        return room.getCode();
    }

    private String validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "닉네임을 입력해 주세요.");
        }
        String name = nickname.trim();
        if (name.length() > MAX_NICKNAME_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "닉네임은 " + MAX_NICKNAME_LENGTH + "자 이하로 입력해 주세요.");
        }
        return name;
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private String newToken() {
        return UUID.randomUUID().toString();
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!roomRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "방 코드를 만들지 못했습니다. 다시 시도해 주세요.");
    }
}