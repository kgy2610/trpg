package com.example.trpg.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class PresenceService {

    public record Offline(Long playerId, String roomCode) {}

    // 연결(sessionId) → 플레이어
    private final Map<String, Long> sessionToPlayer = new HashMap<>();
    // 플레이어 → 열려 있는 연결들 (새로고침하면 새 연결이 먼저 붙고 옛 연결이 나중에 끊길 수 있어서 집합으로 관리)
    private final Map<Long, Set<String>> playerSessions = new HashMap<>();
    private final Map<Long, String> playerRoom = new HashMap<>();

    // 반환값: 이 연결로 오프라인 → 온라인이 되었으면 true
    public synchronized boolean connect(Long playerId, String roomCode, String sessionId) {
        sessionToPlayer.put(sessionId, playerId);
        playerRoom.put(playerId, roomCode);
        Set<String> sessions = playerSessions.computeIfAbsent(playerId, k -> new HashSet<>());
        boolean wasOffline = sessions.isEmpty();
        sessions.add(sessionId);
        return wasOffline;
    }

    // 반환값: 이 연결이 끊기면서 완전히 오프라인이 된 플레이어 (다른 연결이 남아 있으면 empty)
    public synchronized Optional<Offline> disconnect(String sessionId) {
        Long playerId = sessionToPlayer.remove(sessionId);
        if (playerId == null) {
            return Optional.empty();
        }
        Set<String> sessions = playerSessions.get(playerId);
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            playerSessions.remove(playerId);
            String roomCode = playerRoom.remove(playerId);
            return Optional.of(new Offline(playerId, roomCode));
        }
        return Optional.empty();
    }

    public synchronized boolean isOnline(Long playerId) {
        return playerSessions.containsKey(playerId);
    }

    public synchronized Optional<String> roomCodeOf(String sessionId) {
        Long playerId = sessionToPlayer.get(sessionId);
        return playerId == null ? Optional.empty() : Optional.ofNullable(playerRoom.get(playerId));
    }
}