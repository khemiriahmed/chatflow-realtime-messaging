package com.ahmed.chatflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ahmed.chatflow.dto.SendMessageRequest;
import com.ahmed.chatflow.entity.Role;
import com.ahmed.chatflow.repository.ConversationMemberRepository;
import com.ahmed.chatflow.security.CustomUserDetailsService;
import com.ahmed.chatflow.security.JwtService;
import com.ahmed.chatflow.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketInterceptorTest {

    private static final String SECRET = "Y2hhdGZsb3ctZGV2ZWxvcG1lbnQtc2VjcmV0LWtleS1jaGFuZ2UtbWUtMjAyNg==";

    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private ConversationMemberRepository memberRepository;
    @Mock
    private MessageChannel channel;

    private JwtChannelInterceptor jwtInterceptor;
    private AuthorizationChannelInterceptor authorizationInterceptor;
    private UserPrincipal principal;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(SECRET, "chatflow", "chatflow-web",
                Duration.ofSeconds(900), Duration.ofDays(7));
        jwtInterceptor = new JwtChannelInterceptor(jwtService, userDetailsService);
        authorizationInterceptor = new AuthorizationChannelInterceptor(memberRepository, new ObjectMapper(),
                userDetailsService);
        principal = new UserPrincipal(1L, "Alice", "alice@example.com", "hash", Role.USER, true);
        objectMapper = new ObjectMapper();
    }

    @Test
    void connectWithValidBearerSetsPrincipal() {
        when(userDetailsService.loadUserById(1L)).thenReturn(principal);
        String token = new JwtService(SECRET, "chatflow", "chatflow-web",
                Duration.ofSeconds(900), Duration.ofDays(7)).generateAccessToken(principal);

        Message<?> result = jwtInterceptor.preSend(
                frame(StompCommand.CONNECT, null, "Bearer " + token, null), channel);

        Principal user = StompHeaderAccessor.wrap(result).getUser();
        assertInstanceOf(UserPrincipal.class, user);
        assertEquals(1L, ((UserPrincipal) user).getId());
    }

    @Test
    void connectWithoutBearerIsRejected() {
        assertThrows(AccessDeniedException.class,
                () -> jwtInterceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel));
    }

    @Test
    void memberCanSubscribeAndSend() throws Exception {
        when(userDetailsService.loadUserById(1L)).thenReturn(principal);
        when(memberRepository.existsByConversationIdAndUserId(10L, 1L)).thenReturn(true);
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, "/topic/conversation/10", null, null);
        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.wrap(subscribe);
        subscribeAccessor.setLeaveMutable(true);
        subscribeAccessor.setUser(principal);
        Message<byte[]> subscribed = MessageBuilder.createMessage(subscribe.getPayload(),
                subscribeAccessor.getMessageHeaders());
        assertDoesNotThrow(() -> authorizationInterceptor.preSend(subscribed, channel));

        byte[] body = objectMapper.writeValueAsBytes(new SendMessageRequest(10L, "Bonjour", null));
        Message<byte[]> send = frame(StompCommand.SEND, "/app/chat.send", null, body);
        StompHeaderAccessor sendAccessor = StompHeaderAccessor.wrap(send);
        sendAccessor.setLeaveMutable(true);
        sendAccessor.setUser(principal);
        Message<byte[]> sent = MessageBuilder.createMessage(send.getPayload(), sendAccessor.getMessageHeaders());
        assertDoesNotThrow(() -> authorizationInterceptor.preSend(sent, channel));
    }

    @Test
    void nonMemberCannotSubscribeOrSend() throws Exception {
        when(userDetailsService.loadUserById(1L)).thenReturn(principal);
        when(memberRepository.existsByConversationIdAndUserId(10L, 1L)).thenReturn(false);
        byte[] body = objectMapper.writeValueAsBytes(new SendMessageRequest(10L, "Bonjour", null));
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, "/topic/conversation/10", null, null);
        Message<byte[]> send = frame(StompCommand.SEND, "/app/chat.send", null, body);

        assertThrows(AccessDeniedException.class,
                () -> authorizationInterceptor.preSend(withUser(subscribe), channel));
        assertThrows(AccessDeniedException.class,
                () -> authorizationInterceptor.preSend(withUser(send), channel));
    }

    @Test
    void unauthenticatedFrameIsRejected() {
        assertThrows(AccessDeniedException.class, () -> authorizationInterceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/conversation/10", null, null), channel));
    }

    @Test
    void disabledAccountCannotUseStompSession() {
        when(userDetailsService.loadUserById(1L)).thenReturn(
                new UserPrincipal(1L, "Alice", "alice@example.com", "hash", Role.USER, false));
        assertThrows(AccessDeniedException.class, () -> authorizationInterceptor.preSend(
                withUser(frame(StompCommand.SUBSCRIBE, "/topic/conversation/10", null, null)), channel));
    }

    private Message<byte[]> frame(StompCommand command, String destination, String authorization, byte[] body) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(body == null ? new byte[0] : body, accessor.getMessageHeaders());
    }

    private Message<byte[]> withUser(Message<byte[]> message) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        accessor.setLeaveMutable(true);
        accessor.setUser(principal);
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }
}
