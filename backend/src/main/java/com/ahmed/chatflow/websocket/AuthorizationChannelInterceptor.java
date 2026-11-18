package com.ahmed.chatflow.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ahmed.chatflow.security.CustomUserDetailsService;
import com.ahmed.chatflow.security.UserPrincipal;
import com.ahmed.chatflow.repository.ConversationMemberRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AuthorizationChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CONVERSATION_DESTINATION =
            Pattern.compile("^/topic/conversation/(\\d+)(?:/typing)?$");
    private static final Set<String> PROTECTED_SEND_DESTINATIONS =
            Set.of("/app/chat.send", "/app/chat.typing", "/app/chat.read", "/app/chat.delivered");

    private final ConversationMemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final CustomUserDetailsService userDetailsService;

    public AuthorizationChannelInterceptor(ConversationMemberRepository memberRepository, ObjectMapper objectMapper,
                                            CustomUserDetailsService userDetailsService) {
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null || command == StompCommand.DISCONNECT) {
            return message;
        }

        Long userId = authenticatedUserId(accessor);
        if (userId == null) {
            throw new AccessDeniedException("Session STOMP non authentifiée");
        }
        try {
            if (!userDetailsService.loadUserById(userId).isEnabled()) {
                throw new AccessDeniedException("Compte désactivé");
            }
        } catch (UsernameNotFoundException exception) {
            throw new AccessDeniedException("Compte introuvable");
        }

        String destination = accessor.getDestination();
        if (command == StompCommand.SUBSCRIBE && destination != null) {
            Long conversationId = conversationIdFromDestination(destination);
            if (conversationId != null && !isMember(conversationId, userId)) {
                throw new AccessDeniedException("Accès refusé à cette conversation");
            }
        } else if (command == StompCommand.SEND && destination != null
                && PROTECTED_SEND_DESTINATIONS.contains(destination)) {
            Long conversationId = conversationIdFromPayload(message.getPayload());
            if (conversationId == null || !isMember(conversationId, userId)) {
                throw new AccessDeniedException("Accès refusé à cette conversation");
            }
        }
        return message;
    }

    private Long authenticatedUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        if (accessor.getUser() instanceof Authentication authentication && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }

    private boolean isMember(Long conversationId, Long userId) {
        return memberRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    private Long conversationIdFromDestination(String destination) {
        Matcher matcher = CONVERSATION_DESTINATION.matcher(destination);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }

    private Long conversationIdFromPayload(Object payload) {
        try {
            String body = payload instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8)
                    : payload.toString();
            JsonNode node = objectMapper.readTree(body);
            JsonNode conversationId = node.get("conversationId");
            return conversationId != null && conversationId.canConvertToLong() ? conversationId.asLong() : null;
        } catch (Exception exception) {
            return null;
        }
    }
}
