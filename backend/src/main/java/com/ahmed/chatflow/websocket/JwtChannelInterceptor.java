package com.ahmed.chatflow.websocket;

import com.ahmed.chatflow.security.CustomUserDetailsService;
import com.ahmed.chatflow.security.JwtService;
import com.ahmed.chatflow.security.UserPrincipal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtChannelInterceptor(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setLeaveMutable(true);
            String token = extractBearerToken(accessor);
            if (token == null || !jwtService.isAccessTokenValid(token)) {
                throw new AccessDeniedException("Authentification WebSocket invalide");
            }
            Long userId = jwtService.extractUserId(token);
            UserDetails details = userDetailsService.loadUserById(userId);
            if (!details.isEnabled()) {
                throw new AccessDeniedException("Compte désactivé");
            }
            UserPrincipal principal = (UserPrincipal) details;
            accessor.setUser(principal);
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        }
        return message;
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null) {
            authorization = accessor.getFirstNativeHeader("authorization");
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }
}
