package com.ahmed.chatflow.service;

import com.ahmed.chatflow.dto.PresenceEvent;
import com.ahmed.chatflow.dto.NotificationDto;
import com.ahmed.chatflow.entity.User;
import com.ahmed.chatflow.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Set<Long> onlineUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    public PresenceService(UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public void connect(Long userId, String sessionId) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            if (sessionId != null) {
                sessions.put(sessionId, userId);
            }
            onlineUsers.add(userId);
            Instant now = Instant.now();
            user.setLastSeenAt(now);
            userRepository.save(user);
            messagingTemplate.convertAndSend("/topic/presence", new PresenceEvent(userId, true, now));
            messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/notifications",
                    new NotificationDto("PRESENCE", "Connexion établie", null, now));
        });
    }

    @Transactional
    public void disconnect(String sessionId, Long fallbackUserId) {
        Long userId = sessionId == null ? null : sessions.remove(sessionId);
        if (userId == null) {
            userId = fallbackUserId;
        }
        if (userId == null || sessions.containsValue(userId)) {
            return;
        }
        onlineUsers.remove(userId);
        Long disconnectedUserId = userId;
        userRepository.findById(userId).ifPresent(user -> {
            Instant now = Instant.now();
            user.setLastSeenAt(now);
            userRepository.save(user);
            messagingTemplate.convertAndSend("/topic/presence", new PresenceEvent(disconnectedUserId, false, now));
            messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/notifications",
                    new NotificationDto("PRESENCE", "Connexion terminée", null, now));
        });
    }

    public boolean isOnline(Long userId) {
        return userId != null && onlineUsers.contains(userId);
    }
}
