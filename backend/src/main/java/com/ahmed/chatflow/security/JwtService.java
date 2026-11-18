package com.ahmed.chatflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final Duration accessExpiration;
    private final Duration refreshExpiration;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.issuer}") String issuer,
                      @Value("${jwt.audience}") String audience,
                      @Value("${jwt.access-expiration}") Duration accessExpiration,
                      @Value("${jwt.refresh-expiration}") Duration refreshExpiration) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.issuer = issuer;
        this.audience = audience;
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    public String generateAccessToken(UserPrincipal principal) {
        return buildToken(String.valueOf(principal.getId()), principal, "ACCESS", accessExpiration);
    }

    public String generateRefreshToken(Long userId) {
        return buildToken(String.valueOf(userId), null, "REFRESH", refreshExpiration);
    }

    private String buildToken(String subject, UserPrincipal principal, String type, Duration expiration) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .audience().add(audience)
                .and()
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)));
        if (principal != null) {
            builder.claim("email", principal.getUsername())
                    .claim("role", principal.getRole().name());
        }
        return builder.signWith(signingKey).compact();
    }

    public Claims parseClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!claims.getAudience().contains(audience)) {
            throw new JwtException("Audience du jeton invalide");
        }
        return claims;
    }

    public Long extractUserId(String token) {
        try {
            return Long.valueOf(parseClaims(token).getSubject());
        } catch (NumberFormatException exception) {
            throw new JwtException("Sujet du jeton invalide");
        }
    }

    public boolean isAccessTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return "ACCESS".equals(claims.get("typ", String.class)) && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            return "REFRESH".equals(parseClaims(token).get("typ", String.class));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public Duration getRefreshExpiration() {
        return refreshExpiration;
    }

    public long getAccessExpirationSeconds() {
        return accessExpiration.toSeconds();
    }
}
