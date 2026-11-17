package com.example.trpg.config;

import com.example.trpg.domain.Player;
import com.example.trpg.event.LobbyChangedEvent;
import com.example.trpg.repository.PlayerRepository;
import com.example.trpg.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthInterceptor.class);

    // /topic/room/{방코드}/무엇이든  형태의 채널에서 방 코드를 뽑아내는 패턴
    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/room/([^/]+)/.+$");

    private final PlayerRepository playerRepository;
    private final PresenceService presenceService;
    private final ApplicationEventPublisher eventPublisher;

    public StompAuthInterceptor(PlayerRepository playerRepository,
                                PresenceService presenceService,
                                ApplicationEventPublisher eventPublisher) {
        this.playerRepository = playerRepository;
        this.presenceService = presenceService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscribe(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            authorizeSend(accessor);
        }
        return message;
    }

    // 연결(CONNECT) 순간에 토큰을 확인 (기존 코드를 메서드로 분리)
    private void authenticate(StompHeaderAccessor accessor) {
        String token = accessor.getFirstNativeHeader("token");
        if (token == null || token.isBlank()) {
            throw new MessagingException("토큰이 없습니다.");
        }

        Player player = playerRepository.findByTokenWithRoom(token)
                .orElseThrow(() -> new MessagingException("유효하지 않은 토큰입니다."));

        accessor.setUser(new StompPrincipal(String.valueOf(player.getId())));

        String roomCode = player.getRoom().getCode();
        boolean wasOffline = presenceService.connect(player.getId(), roomCode, accessor.getSessionId());
        log.info("접속: {} (playerId={}, 방={}){}", player.getNickname(), player.getId(),
                roomCode, wasOffline ? " [온라인 전환]" : "");

        if (wasOffline) {
            eventPublisher.publishEvent(new LobbyChangedEvent(roomCode));
        }
    }

    // 구독 허용 목록: 내 개인 채널, 내 방의 채널, (임시) 테스트 채널만 허용. 나머지는 모두 거부
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessagingException("구독 대상이 없습니다.");
        }

        // 개인 채널: 서버가 연결의 주인에게만 이어 주므로 항상 허용
        if (destination.startsWith("/user/queue/")) {
            return;
        }
        
        // 내 방의 채널만 허용
        Matcher matcher = ROOM_TOPIC.matcher(destination);
        if (matcher.matches()) {
            String myRoomCode = presenceService.roomCodeOf(accessor.getSessionId()).orElse(null);
            if (matcher.group(1).equals(myRoomCode)) {
                return;
            }
        }

        log.warn("허용되지 않은 구독 시도: session={}, 대상={}", accessor.getSessionId(), destination);
        throw new MessagingException("허용되지 않은 구독입니다: " + destination);
    }

    // 클라이언트는 /app/ 으로만 보낼 수 있음. /topic/ 에 직접 보내면 서버를 거치지 않고 가짜 방송을 만들 수 있어 거부
    private void authorizeSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/app/")) {
            log.warn("허용되지 않은 전송 시도: session={}, 대상={}", accessor.getSessionId(), destination);
            throw new MessagingException("허용되지 않은 전송입니다: " + destination);
        }
    }
}