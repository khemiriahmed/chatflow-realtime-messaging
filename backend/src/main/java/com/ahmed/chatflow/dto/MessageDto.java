package com.ahmed.chatflow.dto;

import java.time.Instant;

public record MessageDto(Long id, Long conversationId, Long senderId, String senderName, String content,
                         Instant createdAt, String status, String clientId) {
}
