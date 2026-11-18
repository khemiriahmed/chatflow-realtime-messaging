package com.ahmed.chatflow.dto;

import java.time.Instant;

public record UserDto(Long id, String name, String email, String role, boolean enabled, boolean online, Instant lastSeenAt) {
}
