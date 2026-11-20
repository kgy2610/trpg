package com.example.trpg.event;

import com.example.trpg.dto.EventLogDtos.Change;

public record EventLogChangedEvent(String roomCode, Change change) {}