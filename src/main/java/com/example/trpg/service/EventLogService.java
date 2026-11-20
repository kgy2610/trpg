package com.example.trpg.service;

import com.example.trpg.domain.EventKind;
import com.example.trpg.domain.EventLogEntry;
import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import com.example.trpg.domain.RoomStatus;
import com.example.trpg.dto.EventLogDtos.Change;
import com.example.trpg.dto.EventLogDtos.EntryView;
import com.example.trpg.event.EventLogChangedEvent;
import com.example.trpg.exception.ApiException;
import com.example.trpg.game.EventLogContent;
import com.example.trpg.game.GameCharacter;
import com.example.trpg.game.GameContent;
import com.example.trpg.repository.EventLogEntryRepository;
import com.example.trpg.repository.PlayerRepository;
import com.example.trpg.repository.RoomRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class EventLogService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final EventLogEntryRepository entryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EventLogService(RoomRepository roomRepository,
                            PlayerRepository playerRepository,
                            EventLogEntryRepository entryRepository,
                            ApplicationEventPublisher eventPublisher) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.entryRepository = entryRepository;
        this.eventPublisher = eventPublisher;
    }

    // 새로고침/입장 때 쓰는 조회. 이 방의 참가자만 볼 수 있고, 지운 기록은 포함하지 않음 (오래된 것부터)
    @Transactional(readOnly = true)
    public List<EntryView> getForPlayer(String code, String token) {
        Room room = findRoom(code);
        requirePlayer(room, token);
        List<EntryView> views = new ArrayList<>();
        for (EventLogEntry e : entryRepository.findTop200ByRoomAndDeletedFalseOrderByIdDesc(room)) {
            views.add(toView(e));
        }
        Collections.reverse(views);
        return views;
    }

    // GM 전용: NPC 선택 목록
    @Transactional(readOnly = true)
    public List<String> getSpeakersForGm(String code, String token) {
        Room room = findRoom(code);
        Player me = requirePlayer(room, token);
        if (!me.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 볼 수 있습니다.");
        }
        return EventLogContent.npcSpeakers();
    }

    @Transactional
    public void send(String code, String token, String speaker, String text) {
        Room room = lockRoom(code);
        Player me = requirePlayer(room, token);

        if (room.getStatus() != RoomStatus.PLAYING) {
            throw new ApiException(HttpStatus.CONFLICT, "게임 진행 중에만 쓸 수 있습니다.");
        }

        String clean = cleanText(text);

        EventKind kind;
        String displayName;
        if (me.isGm()) {
            // GM은 목록에 있는 NPC로만 말할 수 있음
            if (speaker == null || !EventLogContent.npcSpeakers().contains(speaker)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "말할 NPC를 목록에서 골라 주세요.");
            }
            kind = EventKind.NPC;
            displayName = speaker;
        } else {
            if (me.getCharacterKey() == null) {
                throw new ApiException(HttpStatus.CONFLICT, "역할이 아직 배정되지 않았습니다.");
            }
            kind = EventKind.PLAYER;
            displayName = GameContent.displayName(me);
            displayName = GameContent.character(GameCharacter.valueOf(me.getCharacterKey())).name();
        }

        Optional<EventLogEntry> last = entryRepository.findFirstBySenderOrderByIdDesc(me);
        if (last.isPresent()
                && System.currentTimeMillis() - last.get().getCreatedAt().toEpochMilli() < EventLogContent.MIN_INTERVAL_MILLIS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후에 다시 보내 주세요.");
        }

        EventLogEntry saved = entryRepository.save(new EventLogEntry(room, kind, me, displayName, clean, null));
        publishAdded(room, saved);
    }

    // GM이 기록 한 줄을 지움
    @Transactional
    public void delete(String code, String token, long entryId) {
        Room room = lockRoom(code);
        Player me = requirePlayer(room, token);

        if (!me.isGm()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GM만 지울 수 있습니다.");
        }
        if (room.getStatus() != RoomStatus.PLAYING) {
            throw new ApiException(HttpStatus.CONFLICT, "게임 진행 중에만 지울 수 있습니다.");
        }

        EventLogEntry entry = entryRepository.findById(entryId)
                .filter(e -> e.getRoom().getId().equals(room.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 기록입니다."));

        if (entry.isDeleted()) {
            return;
        }
        entry.markDeleted();   // 행은 남기고 표시만 함: 자동 기록이 다시 생기지 않도록
        eventPublisher.publishEvent(new EventLogChangedEvent(room.getCode(), new Change("DELETED", null, entry.getId())));
    }

    // 시계가 새로 도달한 칸의 공개 문장을 자동 기록
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordClockArrival(Room room, int clockValue) {
        String sentence = EventLogContent.clockSentence(clockValue);
        if (sentence != null) {
            recordAuto(room, "CLOCK_" + clockValue, sentence);
        }
    }

    // 단서가 처음 공개될 때 자동 기록
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordClueReveal(Room room, String clueKey, String clueTitle) {
        recordAuto(room, "CLUE_" + clueKey, "단서 공개: " + clueTitle);
    }

    private void recordAuto(Room room, String autoKey, String text) {
        if (entryRepository.existsByRoomAndAutoKey(room, autoKey)) {
            return;
        }
        EventLogEntry saved = entryRepository.save(new EventLogEntry(room, EventKind.SYSTEM, null, null, text, autoKey));
        publishAdded(room, saved);
    }

    private void publishAdded(Room room, EventLogEntry saved) {
        eventPublisher.publishEvent(new EventLogChangedEvent(room.getCode(), new Change("ADDED", toView(saved), null)));
    }

    private EntryView toView(EventLogEntry e) {
        return new EntryView(e.getId(), e.getKind().name(), e.getSpeaker(), e.getContent(),
                e.getCreatedAt().toEpochMilli());
    }

    // 앞뒤 공백 제거
    private String cleanText(String text) {
        if (text == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "내용을 입력해 주세요.");
        }
        String clean = text.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (clean.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "내용을 입력해 주세요.");
        }
        if (clean.length() > EventLogContent.MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, EventLogContent.MAX_LENGTH + "자 이하로 써 주세요.");
        }
        return clean;
    }

    private Room findRoom(String code) {
        return roomRepository.findByCode(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));
    }

    private Room lockRoom(String code) {
        return roomRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 방 코드입니다."));
    }

    // 토큰으로 요청자를 확인, 이 방의 참가자인지 검사
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