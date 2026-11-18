package com.ahmed.chatflow.service;

import com.ahmed.chatflow.dto.MessageDto;
import com.ahmed.chatflow.dto.SendMessageRequest;
import com.ahmed.chatflow.entity.Conversation;
import com.ahmed.chatflow.entity.ConversationMember;
import com.ahmed.chatflow.entity.MemberRole;
import com.ahmed.chatflow.entity.Message;
import com.ahmed.chatflow.entity.ReceiptStatus;
import com.ahmed.chatflow.entity.Role;
import com.ahmed.chatflow.entity.User;
import com.ahmed.chatflow.exception.ForbiddenException;
import com.ahmed.chatflow.mapper.MessageMapper;
import com.ahmed.chatflow.repository.ConversationMemberRepository;
import com.ahmed.chatflow.repository.ConversationRepository;
import com.ahmed.chatflow.repository.MessageReceiptRepository;
import com.ahmed.chatflow.repository.MessageRepository;
import com.ahmed.chatflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageReceiptRepository receiptRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MessageService messageService;
    private User sender;
    private User recipient;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository, receiptRepository, conversationRepository,
                memberRepository, userRepository, messageMapper, messagingTemplate);
        sender = user(1L, "alice@example.com");
        recipient = user(2L, "bob@example.com");
        conversation = new Conversation();
        conversation.setId(10L);
        conversation.getMembers().add(membership(sender));
        conversation.getMembers().add(membership(recipient));
    }

    @Test
    void sendPersistsMessageBeforePublishing() {
        MessageDto expected = new MessageDto(5L, 10L, 1L, "Alice", "Bonjour", Instant.now(), "SENT", null);
        when(conversationRepository.findWithMembersById(10L)).thenReturn(Optional.of(conversation));
        when(memberRepository.findByConversationIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(conversation.getMembers().get(0)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });
        when(messageMapper.toDto(any(Message.class))).thenReturn(expected);

        MessageDto result = messageService.send(1L, new SendMessageRequest(10L, "Bonjour", null));

        assertSame(expected, result);
        InOrder inOrder = inOrder(messageRepository, messagingTemplate);
        inOrder.verify(messageRepository).save(any(Message.class));
        inOrder.verify(messagingTemplate).convertAndSend(eq("/topic/conversation/10"), eq(expected));
        verify(messagingTemplate).convertAndSendToUser(eq("bob@example.com"),
                eq("/queue/notifications"), any());
    }

    @Test
    void sendWithKnownClientIdReturnsExistingMessageWithoutPublishing() {
        Message existing = new Message();
        existing.setId(7L);
        MessageDto expected = new MessageDto(7L, 10L, 1L, "Alice", "Bonjour", Instant.now(), "SENT", "client-1");
        when(conversationRepository.findWithMembersById(10L)).thenReturn(Optional.of(conversation));
        when(memberRepository.findByConversationIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(conversation.getMembers().get(0)));
        when(messageRepository.findBySenderIdAndClientId(1L, "client-1")).thenReturn(Optional.of(existing));
        when(messageMapper.toDto(existing)).thenReturn(expected);

        MessageDto result = messageService.send(1L, new SendMessageRequest(10L, "Bonjour", "client-1"));

        assertSame(expected, result);
        verify(messageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void historyIsForbiddenForNonMember() {
        when(memberRepository.findByConversationIdAndUserId(10L, 3L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> messageService.history(3L, 10L, 0, 20));
        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    void historyReturnsMappedPageForMember() {
        Message message = new Message();
        message.setId(5L);
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent("Bonjour");
        message.setCreatedAt(Instant.now());
        MessageDto dto = new MessageDto(5L, 10L, 1L, "Alice", "Bonjour", message.getCreatedAt(), "SENT", null);
        when(memberRepository.findByConversationIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(conversation.getMembers().get(0)));
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(10L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(message), PageRequest.of(0, 20), 1));
        when(messageMapper.toDto(message)).thenReturn(dto);

        var response = messageService.history(1L, 10L, 0, 20);

        assertEquals(1, response.content().size());
        assertEquals(dto, response.content().get(0));
        assertEquals(1L, response.totalElements());
    }

    @Test
    void markReadCreatesReceiptAndPublishesUpdate() {
        Message message = new Message();
        message.setId(5L);
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent("Bonjour");
        message.setCreatedAt(Instant.now());
        MessageDto dto = new MessageDto(5L, 10L, 1L, "Alice", "Bonjour", message.getCreatedAt(), "READ", null);
        when(memberRepository.findByConversationIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(conversation.getMembers().get(1)));
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(receiptRepository.findByMessageIdAndUserId(5L, 2L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(receiptRepository.save(any(com.ahmed.chatflow.entity.MessageReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messageMapper.toDto(message)).thenReturn(dto);

        MessageDto result = messageService.markRead(2L, 10L, 5L);

        assertSame(dto, result);
        verify(receiptRepository).save(org.mockito.ArgumentMatchers.argThat(receipt ->
                receipt.getStatus() == ReceiptStatus.READ && receipt.getUser().getId().equals(2L)));
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation/10"), eq(dto));
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setName(id == 1L ? "Alice" : "Bob");
        user.setEmail(email);
        user.setRole(Role.USER);
        user.setEnabled(true);
        return user;
    }

    private ConversationMember membership(User user) {
        ConversationMember membership = new ConversationMember();
        membership.setConversation(conversation);
        membership.setUser(user);
        membership.setRole(MemberRole.MEMBER);
        membership.setJoinedAt(Instant.now().minusSeconds(10));
        return membership;
    }
}
