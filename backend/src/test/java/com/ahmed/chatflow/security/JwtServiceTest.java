package com.ahmed.chatflow.security;

import com.ahmed.chatflow.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "Y2hhdGZsb3ctZGV2ZWxvcG1lbnQtc2VjcmV0LWtleS1jaGFuZ2UtbWUtMjAyNg==";

    private JwtService jwtService;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, "chatflow", "chatflow-web",
                Duration.ofSeconds(900), Duration.ofDays(7));
        principal = new UserPrincipal(1L, "Alice", "alice@example.com", "hash", Role.USER, true);
    }

    @Test
    void accessTokenContainsExpectedClaims() {
        String token = jwtService.generateAccessToken(principal);

        Claims claims = jwtService.parseClaims(token);
        assertEquals("1", claims.getSubject());
        assertEquals("ACCESS", claims.get("typ", String.class));
        assertEquals("chatflow", claims.getIssuer());
        assertTrue(claims.getAudience().contains("chatflow-web"));
        assertEquals("alice@example.com", claims.get("email", String.class));
        assertTrue(jwtService.isAccessTokenValid(token));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = jwtService.generateAccessToken(principal);
        JwtService other = new JwtService(
                "b3RoZXItY2hhdGZsb3ctc2VjcmV0LWtleS1sb25nLWVub3VnaC1mb3ItdGVzdHM=",
                "chatflow", "chatflow-web", Duration.ofSeconds(900), Duration.ofDays(7));

        assertThrows(JwtException.class, () -> other.parseClaims(token));
        assertFalse(other.isAccessTokenValid(token));
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() {
        String refreshToken = jwtService.generateRefreshToken(1L);

        assertTrue(jwtService.isRefreshToken(refreshToken));
        assertFalse(jwtService.isAccessTokenValid(refreshToken));
    }

    @Test
    void expiredAccessTokenIsRejected() {
        JwtService expiredService = new JwtService(SECRET, "chatflow", "chatflow-web",
                Duration.ofSeconds(-60), Duration.ofDays(7));
        String token = expiredService.generateAccessToken(principal);

        assertFalse(expiredService.isAccessTokenValid(token));
    }
}
