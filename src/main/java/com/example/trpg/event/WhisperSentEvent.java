package com.example.trpg.event;

public record WhisperSentEvent(String roomCode, Long recipientPlayerId, Long gmPlayerId) {}