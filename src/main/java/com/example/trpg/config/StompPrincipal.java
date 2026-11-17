package com.example.trpg.config;

import java.security.Principal;

// 연결 하나가 "누구인지"를 나타내는 객체. name에는 토큰이 아니라 playerId를 넣음
public record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}