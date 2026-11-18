package com.ahmed.chatflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 120, message = "Le nom ne peut pas dépasser 120 caractères")
        String name) {
}
