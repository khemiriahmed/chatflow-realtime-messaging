package com.ahmed.chatflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ConversationDto(Long id, String type, String title, Instant createdAt, Instant updatedAt,
                              List<UserDto> members, Map<Long, String> memberRoles, MessageDto lastMessage,
                              long unreadCount) {
}
