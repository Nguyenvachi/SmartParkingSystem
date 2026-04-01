package com.parking.smartparking.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Cấu hình WebSocket cho Real-time Communication Tech Key: Tính năng #5 -
 * Real-time Map Update
 *
 * Kiến trúc: - Protocol: STOMP (Simple Text Oriented Messaging Protocol) -
 * Transport: SockJS (Fallback nếu WebSocket không khả dụng)
 *
 * Flow: 1. Client connect đến: ws://localhost:8080/ws 2. Client subscribe
 * topic: /topic/parking-updates 3. Server gửi message khi slot thay đổi 4. Tất
 * cả clients đang subscribe sẽ nhận được update
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Comma-separated patterns, defaults to '*' for dev.
    // Override via env var: APP_WS_ALLOWED_ORIGIN_PATTERNS
    @Value("${app.ws.allowed-origin-patterns:*}")
    private String allowedOriginPatterns;

    @SuppressWarnings("null")
    private String[] parseAllowedOriginPatterns(String csv) {
        final String safeCsv = (csv == null) ? "" : csv;
        return Arrays.stream(safeCsv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    /**
     * Cấu hình Message Broker
     *
     * - /topic: Prefix cho các kênh broadcast (1-to-many) - /app: Prefix cho
     * các message gửi từ client lên server
     */
    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        // Enable simple broker cho topic
        config.enableSimpleBroker("/topic");

        // Prefix cho các message từ client
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Đăng ký WebSocket endpoint
     *
     * - Endpoint: /ws - Cho phép tất cả origins (CORS) - Sử dụng SockJS
     * fallback
     */
    @Override
    @SuppressWarnings("null")
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        final String[] patterns = parseAllowedOriginPatterns(allowedOriginPatterns);

        registry.addEndpoint("/ws")
            // Old (kept): allow all origins (dev only)
            // .setAllowedOriginPatterns("*")
            .setAllowedOriginPatterns(patterns)
                .withSockJS(); // Enable SockJS fallback
    }
}
