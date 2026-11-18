package com.ahmed.chatflow.dto;

import jakarta.validation.constraints.NotNull;

public record ReadEvent(
        @NotNull(message = "L'identifiant de la conversation est obligatoire")
        Long conversationId,
        @NotNull(message = "L'identifiant du message est obligatoire")
        Long messageId) {
}
