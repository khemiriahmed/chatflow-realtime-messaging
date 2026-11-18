package com.ahmed.chatflow.dto;

import java.time.Instant;

public record NotificationDto(String type, String message, Long conversationId, Instant createdAt) {
}
