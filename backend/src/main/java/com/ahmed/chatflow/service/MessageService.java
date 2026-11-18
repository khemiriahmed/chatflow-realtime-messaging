package com.ahmed.chatflow.service;

import com.ahmed.chatflow.dto.MessageDto;
import com.ahmed.chatflow.dto.NotificationDto;
import com.ahmed.chatflow.dto.PageResponse;
import com.ahmed.chatflow.dto.ReadEvent;
import com.ahmed.chatflow.dto.SendMessageRequest;
import com.ahmed.chatflow.entity.Conversation;
import com.ahmed.chatflow.entity.Message;
import com.ahmed.chatflow.entity.MessageReceipt;
import com.ahmed.chatflow.entity.ReceiptStatus;
import com.ahmed.chatflow.entity.User;
import com.ahmed.chatflow.exception.BadRequestException;
import com.ahmed.chatflow.exception.ForbiddenException;
import com.ahmed.chatflow.exception.ResourceNotFoundException;
import com.ahmed.chatflow.mapper.MessageMapper;
import com.ahmed.chatflow.repository.ConversationMemberRepository;
import com.ahmed.chatflow.repository.ConversationRepository;
import com.ahmed.chatflow.repository.MessageReceiptRepository;
import com.ahmed.chatflow.repository.MessageRepository;
import com.ahmed.chatflow.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final MessageMapper messageMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(MessageRepository messageRepository, MessageReceiptRepository receiptRepository,
                          ConversationRepository conversationRepository,
                          ConversationMemberRepository memberRepository,
                          UserRepository userRepository, MessageMapper messageMapper,
                          SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.messageMapper = messageMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public MessageDto send(Long senderId, SendMessageRequest request) {
        if (request == null || request.conversationId() == null) {
            throw new BadRequestException("L'identifiant de la conversation est obligatoire");
        }
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new BadRequestException("Le message ne peut pas être vide");
        }
        if (content.length() > 4000) {
            throw new BadRequestException("Le message ne peut pas dépasser 4000 caractères");
        }
        if (request.clientId() != null && request.clientId().length() > 100) {
            throw new BadRequestException("L'identifiant client ne peut pas dépasser 100 caractères");
        }
        Conversation conversation = conversationRepository.findWithMembersById(request.conversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation introuvable"));
        memberRepository.findByConversationIdAndUserId(request.conversationId(), senderId)
                .orElseThrow(() -> new ForbiddenException("Vous n'êtes pas membre de cette conversation"));

        String clientId = request.clientId() == null || request.clientId().isBlank() ? null : request.clientId();
        if (clientId != null) {
            Message existing = messageRepository.findBySenderIdAndClientId(senderId, clientId).orElse(null);
            if (existing != null) {
                return messageMapper.toDto(existing);
            }
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        Instant now = Instant.now();
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        message.setCreatedAt(now);
        message.setClientId(clientId);
        Message saved = messageRepository.save(message);
        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);

        MessageDto dto = messageMapper.toDto(saved);
        messagingTemplate.convertAndSend("/topic/conversation/" + conversation.getId(), dto);
        NotificationDto notification = new NotificationDto("MESSAGE", "Nouveau message dans une conversation",
                conversation.getId(), now);
        conversation.getMembers().forEach(member -> {
            messagingTemplate.convertAndSendToUser(member.getUser().getEmail(), "/queue/messages", dto);
            if (!member.getUser().getId().equals(senderId)) {
                messagingTemplate.convertAndSendToUser(member.getUser().getEmail(),
                        "/queue/notifications", notification);
            }
        });
        return dto;
    }

    public PageResponse<MessageDto> history(Long userId, Long conversationId, int page, int size) {
        requireMembership(conversationId, userId);
        return PageResponse.of(messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(page, size))
                .map(messageMapper::toDto));
    }

    @Transactional
    public MessageDto markDelivered(Long userId, Long conversationId, Long messageId) {
        return updateReceipt(userId, conversationId, messageId, ReceiptStatus.DELIVERED);
    }

    @Transactional
    public MessageDto markRead(Long userId, Long conversationId, Long messageId) {
        return updateReceipt(userId, conversationId, messageId, ReceiptStatus.READ);
    }

    @Transactional
    public void markConversationRead(Long userId, Long conversationId) {
        requireMembership(conversationId, userId);
        messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 200))
                .forEach(message -> {
                    if (!message.getSender().getId().equals(userId)) {
                        updateReceipt(userId, conversationId, message.getId(), ReceiptStatus.READ);
                    }
                });
    }

    private MessageDto updateReceipt(Long userId, Long conversationId, Long messageId, ReceiptStatus status) {
        if (messageId == null) {
            throw new BadRequestException("L'identifiant du message est obligatoire");
        }
        requireMembership(conversationId, userId);
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message introuvable"));
        if (!message.getConversation().getId().equals(conversationId)) {
            throw new BadRequestException("Ce message n'appartient pas à cette conversation");
        }
        if (message.getSender().getId().equals(userId)) {
            return messageMapper.toDto(message);
        }
        MessageReceipt receipt = receiptRepository.findByMessageIdAndUserId(messageId, userId).orElse(null);
        if (receipt != null && receipt.getStatus() == ReceiptStatus.READ && status == ReceiptStatus.DELIVERED) {
            return messageMapper.toDto(message);
        }
        if (receipt == null) {
            receipt = new MessageReceipt();
            receipt.setMessage(message);
            receipt.setUser(userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable")));
        }
        receipt.setStatus(status);
        receipt.setUpdatedAt(Instant.now());
        MessageReceipt saved = receiptRepository.save(receipt);
        if (!message.getReceipts().contains(saved)) {
            message.getReceipts().add(saved);
        }
        MessageDto dto = messageMapper.toDto(message);
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, dto);
        return dto;
    }

    private void requireMembership(Long conversationId, Long userId) {
        memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException("Vous n'êtes pas membre de cette conversation"));
    }
}
