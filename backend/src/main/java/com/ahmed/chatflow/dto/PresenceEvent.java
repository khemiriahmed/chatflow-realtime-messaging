package com.ahmed.chatflow.dto;

import java.time.Instant;

public record PresenceEvent(Long userId, boolean online, Instant at) {
}
