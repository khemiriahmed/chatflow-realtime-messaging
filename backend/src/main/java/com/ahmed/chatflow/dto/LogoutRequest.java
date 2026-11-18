package com.ahmed.chatflow.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank(message = "Le jeton de rafraîchissement est obligatoire") String refreshToken) {
}
