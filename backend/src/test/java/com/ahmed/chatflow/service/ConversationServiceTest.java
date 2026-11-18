package com.ahmed.chatflow.service;

import com.ahmed.chatflow.dto.AddMemberRequest;
import com.ahmed.chatflow.dto.ConversationDto;
import com.ahmed.chatflow.dto.CreateConversationRequest;
import com.ahmed.chatflow.entity.Conversation;
import com.ahmed.chatflow.entity.ConversationMember;
import com.ahmed.chatflow.entity.ConversationType;
import com.ahmed.chatflow.entity.MemberRole;
import com.ahmed.chatflow.entity.ReceiptStatus;
import com.ahmed.chatflow.entity.Role;
import com.ahmed.chatflow.entity.User;
import com.ahmed.chatflow.exception.BadRequestException;
import com.ahmed.chatflow.exception.ForbiddenException;
import com.ahmed.chatflow.mapper.ConversationMapper;
import com.ahmed.chatflow.mapper.MessageMapper;
import com.ahmed.chatflow.repository.ConversationMemberRepository;
import com.ahmed.chatflow.repository.ConversationRepository;
import com.ahmed.chatflow.repository.MessageReceiptRepository;
import com.ahmed.chatflow.repository.MessageRepository;
import com.ahmed.chatflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageReceiptRepository receiptRepository;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(conversationRepository, memberRepository, userRepository,
                messageRepository, receiptRepository, conversationMapper, messageMapper, messagingTemplate);
    }

    @Test
    void privateConversationCreationReusesExistingPair() {
        Conversation existing = conversation(10L, ConversationType.PRIVATE);
        existing.getMembers().add(membership(existing, user(1L, "alice@example.com"), MemberRole.MEMBER));
        existing.getMembers().add(membership(existing, user(2L, "bob@example.com"), MemberRole.MEMBER));
        ConversationDto expected = org.mockito.Mockito.mock(ConversationDto.class);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "bob@example.com")));
        when(conversationRepository.findPrivateBetween(ConversationType.PRIVATE, 1L, 2L))
                .thenReturn(Optional.of(existing));
        stubConversationDto(existing, expected);

        ConversationDto result = conversationService.create(1L,
                new CreateConversationRequest(ConversationType.PRIVATE, null, List.of(2L)));

        assertSame(expected, result);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void privateConversationCreationAddsExactlyTwoMembers() {
        ConversationDto expected = org.mockito.Mockito.mock(ConversationDto.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "alice@example.com")));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "bob@example.com")));
        when(conversationRepository.findPrivateBetween(ConversationType.PRIVATE, 1L, 2L))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(conversationMapper.toDto(any(Conversation.class), isNull(), eq(0L))).thenReturn(expected);

        ConversationDto result = conversationService.create(1L,
                new CreateConversationRequest(ConversationType.PRIVATE, null, List.of(2L)));

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getMembers().size());
        assertEquals(MemberRole.MEMBER, captor.getValue().getMembers().get(0).getRole());
        assertSame(expected, result);
        verify(messagingTemplate).convertAndSendToUser(eq("bob@example.com"),
                eq("/queue/notifications"), any());
    }

    @Test
    void groupCreationRequiresTitle() {
        assertThrows(BadRequestException.class, () -> conversationService.create(1L,
                new CreateConversationRequest(ConversationType.GROUP, "  ", List.of(2L))));
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void plainMemberCannotAddMembers() {
        Conversation conversation = conversation(10L, ConversationType.GROUP);
        conversation.getMembers().add(membership(conversation, user(1L, "alice@example.com"), MemberRole.MEMBER));
        when(conversationRepository.findWithMembersById(10L)).thenReturn(Optional.of(conversation));

        assertThrows(ForbiddenException.class, () -> conversationService.addMember(1L, 10L,
                new AddMemberRequest(2L)));
        verify(memberRepository, never()).save(any());
    }

    @Test
    void ownerCanAddMemberWithMemberRole() {
        Conversation conversation = conversation(10L, ConversationType.GROUP);
        conversation.getMembers().add(membership(conversation, user(1L, "alice@example.com"), MemberRole.OWNER));
        User invited = user(2L, "bob@example.com");
        ConversationDto expected = org.mockito.Mockito.mock(ConversationDto.class);
        when(conversationRepository.findWithMembersById(10L)).thenReturn(Optional.of(conversation));
        when(userRepository.findById(2L)).thenReturn(Optional.of(invited));
        when(memberRepository.existsByConversationIdAndUserId(10L, 2L)).thenReturn(false);
        when(memberRepository.save(any(ConversationMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubConversationDto(conversation, expected);

        ConversationDto result = conversationService.addMember(1L, 10L, new AddMemberRequest(2L));

        assertSame(expected, result);
        ArgumentCaptor<ConversationMember> captor = ArgumentCaptor.forClass(ConversationMember.class);
        verify(memberRepository).save(captor.capture());
        assertEquals(MemberRole.MEMBER, captor.getValue().getRole());
        assertEquals(2L, captor.getValue().getUser().getId());
    }

    @Test
    void ownerCannotBeRemoved() {
        Conversation conversation = conversation(10L, ConversationType.GROUP);
        conversation.getMembers().add(membership(conversation, user(1L, "alice@example.com"), MemberRole.OWNER));
        ConversationMember otherOwner = membership(conversation, user(2L, "bob@example.com"), MemberRole.OWNER);
        conversation.getMembers().add(otherOwner);
        when(conversationRepository.findWithMembersById(10L)).thenReturn(Optional.of(conversation));
        when(memberRepository.findByConversationIdAndUserId(10L, 2L)).thenReturn(Optional.of(otherOwner));

        assertThrows(ForbiddenException.class, () -> conversationService.removeMember(1L, 10L, 2L));
        verify(memberRepository, never()).delete(any());
    }

    @Test
    void lastOwnerCannotLeaveGroup() {
        Conversation conversation = conversation(10L, ConversationType.GROUP);
        conversation.getMembers().add(membership(conversation, user(1L, "alice@example.com"), MemberRole.OWNER));
        when(conversationRepository.findWithMembersById(10L)).thenReturn(Optional.of(conversation));
        when(memberRepository.countByConversationIdAndRole(10L, MemberRole.OWNER)).thenReturn(1L);

        assertThrows(ForbiddenException.class, () -> conversationService.leave(1L, 10L));
        verify(memberRepository, never()).delete(any());
    }

    @Test
    void nonMemberCannotReadConversation() {
        when(conversationRepository.findWithMembersById(10L))
                .thenReturn(Optional.of(conversation(10L, ConversationType.PRIVATE)));

        assertThrows(ForbiddenException.class, () -> conversationService.get(3L, 10L));
    }

    private void stubConversationDto(Conversation conversation, ConversationDto expected) {
        when(messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId()))
                .thenReturn(Optional.empty());
        when(receiptRepository.findLastReceiptAt(eq(conversation.getId()), anyLong(), eq(ReceiptStatus.READ)))
                .thenReturn(Optional.of(Instant.now()));
        when(messageRepository.countUnread(eq(conversation.getId()), anyLong(), any(Instant.class)))
                .thenReturn(0L);
        when(conversationMapper.toDto(eq(conversation), isNull(), eq(0L))).thenReturn(expected);
    }

    private Conversation conversation(Long id, ConversationType type) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setType(type);
        conversation.setTitle(type == ConversationType.GROUP ? "Groupe" : null);
        conversation.setCreatedAt(Instant.now().minusSeconds(10));
        conversation.setUpdatedAt(Instant.now());
        return conversation;
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setName("User" + id);
        user.setEmail(email);
        user.setRole(Role.USER);
        user.setEnabled(true);
        return user;
    }

    private ConversationMember membership(Conversation conversation, User user, MemberRole role) {
        ConversationMember membership = new ConversationMember();
        membership.setConversation(conversation);
        membership.setUser(user);
        membership.setRole(role);
        membership.setJoinedAt(Instant.now().minusSeconds(5));
        return membership;
    }
}
