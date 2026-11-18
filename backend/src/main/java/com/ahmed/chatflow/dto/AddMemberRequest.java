package com.ahmed.chatflow.dto;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(@NotNull(message = "L'identifiant de l'utilisateur est obligatoire") Long userId) {
}
