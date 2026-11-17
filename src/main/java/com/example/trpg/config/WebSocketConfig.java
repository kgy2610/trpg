package com.example.trpg.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuthInterceptor;

    public WebSocketConfig(StompAuthInterceptor stompAuthInterceptor) {
        this.stompAuthInterceptor = stompAuthInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 브라우저가 접속하는 주소: ws://서버/ws
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic: 방 전체 공개용, /queue: 개인 전달용
        registry.enableSimpleBroker("/topic", "/queue");
        // 클라이언트가 서버로 보낼 때 붙이는 접두사
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 클라이언트가 보내는 모든 STOMP 메시지가 이 인터셉터를 먼저 거침
        registration.interceptors(stompAuthInterceptor);
    }
}