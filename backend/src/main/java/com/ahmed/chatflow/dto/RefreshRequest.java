package com.ahmed.chatflow.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "Le jeton de rafraîchissement est obligatoire") String refreshToken) {
}
