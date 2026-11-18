package com.ahmed.chatflow.service;

import com.ahmed.chatflow.dto.AdminStatsDto;
import com.ahmed.chatflow.entity.User;
import com.ahmed.chatflow.repository.ConversationRepository;
import com.ahmed.chatflow.repository.MessageRepository;
import com.ahmed.chatflow.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public AdminService(UserRepository userRepository, ConversationRepository conversationRepository,
                        MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public AdminStatsDto stats() {
        long usersCount = userRepository.count();
        long activeUsersCount = userRepository.countByEnabledTrue();
        long conversationsCount = conversationRepository.count();
        long messagesCount = messageRepository.count();
        return new AdminStatsDto(usersCount, activeUsersCount, conversationsCount, messagesCount);
    }
}
