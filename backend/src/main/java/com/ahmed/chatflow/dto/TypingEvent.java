package com.ahmed.chatflow.dto;

import jakarta.validation.constraints.NotNull;

public record TypingEvent(
        @NotNull(message = "L'identifiant de la conversation est obligatoire")
        Long conversationId,
        boolean typing) {
}
