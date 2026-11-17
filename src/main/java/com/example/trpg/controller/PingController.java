//확인용 임시 코드, 확인 후 삭제하기

package com.example.trpg.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class PingController {

    // 보낸 사람에게만 응답 (개인 채널). 비밀 카드 전달도 이 원리를 씀
    @MessageMapping("/ping")
    @SendToUser("/queue/pong")
    public String pingMe(Principal principal) {
        return "개인 응답 → playerId=" + principal.getName();
    }

    // 구독 중인 모두에게 응답 (전체 채널)
    @MessageMapping("/ping-all")
    @SendTo("/topic/ping")
    public String pingAll(Principal principal) {
        return "전체 응답 ← playerId=" + principal.getName();
    }
}