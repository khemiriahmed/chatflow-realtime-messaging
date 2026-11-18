package com.ahmed.chatflow.dto;

public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserDto user) {
}
