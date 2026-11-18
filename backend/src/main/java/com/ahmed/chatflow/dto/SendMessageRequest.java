package com.ahmed.chatflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotNull(message = "L'identifiant de la conversation est obligatoire")
        Long conversationId,
        @NotBlank(message = "Le contenu du message est obligatoire")
        @Size(max = 4000, message = "Le message ne peut pas dépasser 4000 caractères")
        String content,
        @Size(max = 100, message = "L'identifiant client ne peut pas dépasser 100 caractères")
        String clientId) {
}
