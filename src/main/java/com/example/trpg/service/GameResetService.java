package com.example.trpg.service;

import com.example.trpg.domain.Player;
import com.example.trpg.domain.Room;
import com.example.trpg.repository.ClueRevealRepository;
import com.example.trpg.repository.DiceRollRepository;
import com.example.trpg.repository.PlayerRepository;
import com.example.trpg.repository.WhisperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameResetService {

    private final PlayerRepository playerRepository;
    private final ClueRevealRepository clueRevealRepository;
    private final DiceRollRepository diceRollRepository;
    private final WhisperRepository whisperRepository;

    public GameResetService(PlayerRepository playerRepository,
                            ClueRevealRepository clueRevealRepository,
                            DiceRollRepository diceRollRepository,
                            WhisperRepository whisperRepository) {
        this.playerRepository = playerRepository;
        this.clueRevealRepository = clueRevealRepository;
        this.diceRollRepository = diceRollRepository;
        this.whisperRepository = whisperRepository;
    }

    // 방을 대기 상태로 되돌리고 게임 기록(귓속말, 판정, 단서 공개)과 참가자의 진행 상태를 모두 지움
    // MANDATORY: 호출하는 쪽이 이미 트랜잭션 안에서 방을 잠근 상태여야 함. 그렇지 않으면 바로 에러가 나서 실수를 알 수 있음
    @Transactional(propagation = Propagation.MANDATORY)
    public void resetRoom(Room room) {
        whisperRepository.deleteByRoom(room);
        diceRollRepository.deleteByRoom(room);
        clueRevealRepository.deleteByRoom(room);
        for (Player player : playerRepository.findByRoomOrderByIdAsc(room)) {
            player.resetGame();
        }
        room.resetToWaiting();
    }
}