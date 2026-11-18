package com.ahmed.chatflow.service;

import com.ahmed.chatflow.dto.AddMemberRequest;
import com.ahmed.chatflow.dto.ConversationDto;
import com.ahmed.chatflow.dto.CreateConversationRequest;
import com.ahmed.chatflow.dto.NotificationDto;
import com.ahmed.chatflow.entity.Conversation;
import com.ahmed.chatflow.entity.ConversationMember;
import com.ahmed.chatflow.entity.ConversationType;
import com.ahmed.chatflow.entity.MemberRole;
import com.ahmed.chatflow.entity.ReceiptStatus;
import com.ahmed.chatflow.entity.User;
import com.ahmed.chatflow.exception.BadRequestException;
import com.ahmed.chatflow.exception.ConflictException;
import com.ahmed.chatflow.exception.ForbiddenException;
import com.ahmed.chatflow.exception.ResourceNotFoundException;
import com.ahmed.chatflow.mapper.ConversationMapper;
import com.ahmed.chatflow.mapper.MessageMapper;
import com.ahmed.chatflow.repository.ConversationMemberRepository;
import com.ahmed.chatflow.repository.ConversationRepository;
import com.ahmed.chatflow.repository.MessageReceiptRepository;
import com.ahmed.chatflow.repository.MessageRepository;
import com.ahmed.chatflow.repository.UserRepository;
import com.ahmed.chatflow.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMemberRepository memberRepository,
                               UserRepository userRepository,
                               MessageRepository messageRepository,
                               MessageReceiptRepository receiptRepository,
                               ConversationMapper conversationMapper,
                               MessageMapper messageMapper,
                               SimpMessagingTemplate messagingTemplate) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.messagingTemplate = messagingTemplate;
    }

    public PageResponse<ConversationDto> list(Long userId, int page, int size) {
        return PageResponse.of(conversationRepository.findByUserId(userId, PageRequest.of(page, size))
                .map(conversation -> toDto(conversation, userId)));
    }

    public ConversationDto get(Long userId, Long conversationId) {
        Conversation conversation = findConversation(conversationId);
        requireMembership(conversation, userId);
        return toDto(conversation, userId);
    }

    @Transactional
    public ConversationDto create(Long userId, CreateConversationRequest request) {
        if (request.type() == null) {
            throw new BadRequestException("Le type de conversation est obligatoire");
        }
        List<Long> requestedIds = request.memberIds() == null ? List.of()
                : request.memberIds().stream().filter(Objects::nonNull).distinct().toList();
        List<Long> memberIds = requestedIds.stream().filter(id -> !id.equals(userId)).toList();

        if (request.type() == ConversationType.PRIVATE) {
            return createPrivate(userId, memberIds);
        }
        return createGroup(userId, request.title(), memberIds);
    }

    private ConversationDto createPrivate(Long userId, List<Long> memberIds) {
        if (memberIds.size() != 1) {
            throw new BadRequestException("Une conversation privée doit contenir exactement deux membres");
        }
        User other = findUser(memberIds.get(0));
        Optional<Conversation> existing = conversationRepository.findPrivateBetween(
                ConversationType.PRIVATE, userId, other.getId());
        if (existing.isPresent()) {
            return toDto(existing.get(), userId);
        }

        Instant now = Instant.now();
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.PRIVATE);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversation.addMember(member(userId, MemberRole.MEMBER, now));
        conversation.addMember(member(other.getId(), MemberRole.MEMBER, now));
        Conversation saved = conversationRepository.save(conversation);
        notifyMembers(saved, userId, "Nouvelle conversation privée");
        return toDto(saved, userId);
    }

    private ConversationDto createGroup(Long userId, String title, List<Long> memberIds) {
        if (title == null || title.isBlank()) {
            throw new BadRequestException("Le titre du groupe est obligatoire");
        }
        if (memberIds.size() + 1 < 2) {
            throw new BadRequestException("Un groupe doit contenir au moins deux membres");
        }
        List<User> invited = userRepository.findAllByIdIn(memberIds);
        if (invited.size() != memberIds.size()) {
            throw new ResourceNotFoundException("Un utilisateur invité est introuvable");
        }

        Instant now = Instant.now();
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.GROUP);
        conversation.setTitle(title.trim());
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversation.addMember(member(userId, MemberRole.OWNER, now));
        invited.forEach(user -> conversation.addMember(member(user.getId(), MemberRole.MEMBER, now)));
        Conversation saved = conversationRepository.save(conversation);
        notifyMembers(saved, userId, "Vous avez été ajouté au groupe " + saved.getTitle());
        return toDto(saved, userId);
    }

    @Transactional
    public ConversationDto addMember(Long actorId, Long conversationId, AddMemberRequest request) {
        if (request.userId() == null) {
            throw new BadRequestException("L'identifiant de l'utilisateur est obligatoire");
        }
        Conversation conversation = findConversation(conversationId);
        ConversationMember actor = requireMembership(conversation, actorId);
        requireGroup(conversation);
        requireManager(actor);
        User invited = findUser(request.userId());
        if (memberRepository.existsByConversationIdAndUserId(conversationId, invited.getId())) {
            throw new ConflictException("Cet utilisateur est déjà membre de la conversation");
        }
        ConversationMember newMember = member(invited.getId(), MemberRole.MEMBER, Instant.now());
        conversation.addMember(newMember);
        memberRepository.save(newMember);
        conversationRepository.save(conversation);
        messagingTemplate.convertAndSendToUser(invited.getEmail(), "/queue/notifications",
                new NotificationDto("CONVERSATION", "Vous avez été ajouté à une conversation",
                        conversationId, Instant.now()));
        return toDto(conversation, actorId);
    }

    @Transactional
    public void removeMember(Long actorId, Long conversationId, Long targetUserId) {
        Conversation conversation = findConversation(conversationId);
        ConversationMember actor = requireMembership(conversation, actorId);
        requireGroup(conversation);
        requireManager(actor);
        ConversationMember target = memberRepository.findByConversationIdAndUserId(conversationId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Membre introuvable dans cette conversation"));
        if (target.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("Le propriétaire du groupe ne peut pas être retiré");
        }
        if (targetUserId.equals(actorId)) {
            throw new BadRequestException("Utilisez la sortie du groupe pour quitter la conversation");
        }
        conversation.removeMember(target);
        memberRepository.delete(target);
        conversationRepository.save(conversation);
        messagingTemplate.convertAndSendToUser(target.getUser().getEmail(), "/queue/notifications",
                new NotificationDto("CONVERSATION", "Vous avez été retiré d'une conversation",
                        conversationId, Instant.now()));
    }

    @Transactional
    public void leave(Long userId, Long conversationId) {
        Conversation conversation = findConversation(conversationId);
        ConversationMember leaving = requireMembership(conversation, userId);
        requireGroup(conversation);
        if (leaving.getRole() == MemberRole.OWNER
                && memberRepository.countByConversationIdAndRole(conversationId, MemberRole.OWNER) <= 1) {
            throw new ForbiddenException("Le dernier propriétaire ne peut pas quitter le groupe");
        }
        conversation.removeMember(leaving);
        memberRepository.delete(leaving);
        if (leaving.getRole() == MemberRole.OWNER) {
            conversation.getMembers().stream()
                    .filter(current -> current.getRole() != MemberRole.OWNER)
                    .findFirst()
                    .ifPresent(current -> current.setRole(MemberRole.OWNER));
        }
        conversationRepository.save(conversation);
    }

    public ConversationMember requireMembership(Long conversationId, Long userId) {
        return requireMembership(findConversation(conversationId), userId);
    }

    private ConversationMember requireMembership(Conversation conversation, Long userId) {
        return conversation.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("Vous n'êtes pas membre de cette conversation"));
    }

    private void requireGroup(Conversation conversation) {
        if (conversation.getType() != ConversationType.GROUP) {
            throw new BadRequestException("Cette action n'est disponible que pour les groupes");
        }
    }

    private void requireManager(ConversationMember member) {
        if (member.getRole() != MemberRole.OWNER && member.getRole() != MemberRole.ADMIN) {
            throw new ForbiddenException("Seuls les propriétaires et administrateurs peuvent gérer les membres");
        }
    }

    private Conversation findConversation(Long conversationId) {
        return conversationRepository.findWithMembersById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation introuvable"));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    private ConversationMember member(Long userId, MemberRole role, Instant joinedAt) {
        ConversationMember member = new ConversationMember();
        member.setUser(findUser(userId));
        member.setRole(role);
        member.setJoinedAt(joinedAt);
        return member;
    }

    private void notifyMembers(Conversation conversation, Long actorId, String text) {
        Instant now = Instant.now();
        conversation.getMembers().stream()
                .filter(member -> !member.getUser().getId().equals(actorId))
                .forEach(member -> messagingTemplate.convertAndSendToUser(member.getUser().getEmail(),
                        "/queue/notifications", new NotificationDto("CONVERSATION", text, conversation.getId(), now)));
    }

    private ConversationDto toDto(Conversation conversation, Long userId) {
        var lastMessage = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(messageMapper::toDto)
                .orElse(null);
        Instant since = receiptRepository.findLastReceiptAt(conversation.getId(), userId, ReceiptStatus.READ)
                .orElseGet(() -> memberRepository.findByConversationIdAndUserId(conversation.getId(), userId)
                        .map(ConversationMember::getJoinedAt)
                        .orElse(Instant.EPOCH));
        long unreadCount = messageRepository.countUnread(conversation.getId(), userId, since);
        return conversationMapper.toDto(conversation, lastMessage, unreadCount);
    }
}
