package com.ahmed.chatflow.dto;

import com.ahmed.chatflow.entity.ConversationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateConversationRequest(
        @NotNull(message = "Le type de conversation est obligatoire")
        ConversationType type,
        @Size(max = 200, message = "Le titre ne peut pas dépasser 200 caractères")
        String title,
        List<Long> memberIds) {
}
