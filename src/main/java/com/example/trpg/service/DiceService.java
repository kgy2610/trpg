package com.example.trpg.service;

import com.example.trpg.domain.DiceRoll;
import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import com.example.trpg.domain.RoomStatus;
import com.example.trpg.dto.DiceDtos.RollView;
import com.example.trpg.dto.GameDtos.CharacterCard;
import com.example.trpg.exception.ApiException;
import com.example.trpg.game.DiceOutcome;
import com.example.trpg.game.GameCharacter;
import com.example.trpg.game.GameContent;
import com.example.trpg.game.Stat;
import com.example.trpg.repository.DiceRollRepository;
import com.example.trpg.repository.PlayerRepository;
import com.example.trpg.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DiceService {

    // 더블클릭으로 의도치 않게 두 번 굴러지는 것을 막는 최소 간격 (밀리초)
    private static final long COOLDOWN_MILLIS = 3000;
    private static final int MAX_REASON_LENGTH = 30;

    private final SecureRandom random = new SecureRandom();
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final DiceRollRepository diceRollRepository;

    public DiceService(RoomRepository roomRepository,
                        PlayerRepository playerRepository,
                        DiceRollRepository diceRollRepository) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.diceRollRepository = diceRollRepository;
    }

    // 플레이어가 주사위를 굴림. 반환값: 방송에 쓸 방 코드
    @Transactional
    public String roll(String code, String token, String statName, String reason) {
        Room room = lockRoom(code);
        Player me = requirePlayer(room, token);

        if (me.isGm()) {
            throw new ApiException(HttpStatus.CONFLICT, "GM은 주사위를 굴릴 수 없습니다. 플레이어가 굴립니다.");
        }
        if (room.getStatus() != RoomStatus.PLAYING) {
            throw new ApiException(HttpStatus.CONFLICT, "게임 진행 중에만 주사위를 굴릴 수 있습니다.");
        }
        if (me.getCharacterKey() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "역할이 아직 배정되지 않았습니다.");
        }

        Stat stat = Stat.parse(statName)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "능력치는 STRENGTH, MIND, CHARM 중 하나여야 합니다."));

        String cleanReason = null;
        if (reason != null && !reason.isBlank()) {
            cleanReason = reason.trim();
            if (cleanReason.length() > MAX_REASON_LENGTH) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "행동 설명은 " + MAX_REASON_LENGTH + "자 이하로 써 주세요.");
            }
        }

        // 쿨다운: 방 행이 잠겨 있어서 같은 사람의 요청이 동시에 와도 순서대로 검사됨
        Optional<DiceRoll> last = diceRollRepository.findFirstByPlayerOrderByIdDesc(me);
        if (last.isPresent()
                && System.currentTimeMillis() - last.get().getCreatedAt().toEpochMilli() < COOLDOWN_MILLIS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후에 다시 굴려 주세요.");
        }

        // 능력치 값은 요청이 아니라 서버가 아는 "내 캐릭터"의 값을 사용
        CharacterCard card = GameContent.character(GameCharacter.valueOf(me.getCharacterKey()));
        int modifier = switch (stat) {
            case STRENGTH -> card.strength();
            case MIND -> card.mind();
            case CHARM -> card.charm();
        };

        int die1 = random.nextInt(6) + 1;
        int die2 = random.nextInt(6) + 1;
        int total = die1 + die2 + modifier;
        DiceOutcome outcome = DiceOutcome.of(total);

        diceRollRepository.save(new DiceRoll(room, me, stat.name(), die1, die2, modifier, total, outcome.name(), cleanReason));
        return room.getCode();
    }

    // 방송용: 방 코드로 최근 판정을 조회 (서버 내부에서만 호출)
    @Transactional(readOnly = true)
    public List<RollView> listRecent(String roomCode) {
        return recentViews(findRoom(roomCode));
    }

    // 새로고침 때 쓰는 조회. 이 방의 참가자만 볼 수 있음
    @Transactional(readOnly = true)
    public List<RollView> getRecentForPlayer(String code, String token) {
        Room room = findRoom(code);
        requirePlayer(room, token);
        return recentViews(room);
    }

    private List<RollView> recentViews(Room room) {
        return diceRollRepository.findTop20ByRoomOrderByIdDesc(room).stream()
                .map(r -> {
                    Player p = r.getPlayer();
                    String characterName = p.getCharacterKey() == null
                            ? ""
                            : GameContent.displayName(p);
                    Stat stat = Stat.valueOf(r.getStat());
                    DiceOutcome outcome = DiceOutcome.valueOf(r.getOutcome());
                    return new RollView(
                            r.getId(), p.getNickname(), characterName,
                            stat.name(), stat.label(),
                            r.getDie1(), r.getDie2(), r.getModifier(), r.getTotal(),
                            outcome.name(), outcome.label(),
                            r.getReason(), r.getCreatedAt().toEpochMilli());
                })
                .toList();
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