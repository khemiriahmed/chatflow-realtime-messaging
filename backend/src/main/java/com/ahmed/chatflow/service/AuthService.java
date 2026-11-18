package com.ahmed.chatflow.service;

import com.ahmed.chatflow.dto.AuthResponse;
import com.ahmed.chatflow.dto.LoginRequest;
import com.ahmed.chatflow.dto.LogoutRequest;
import com.ahmed.chatflow.dto.RefreshRequest;
import com.ahmed.chatflow.dto.RegisterRequest;
import com.ahmed.chatflow.entity.RefreshToken;
import com.ahmed.chatflow.entity.Role;
import com.ahmed.chatflow.entity.User;
import com.ahmed.chatflow.exception.ConflictException;
import com.ahmed.chatflow.exception.UnauthorizedException;
import com.ahmed.chatflow.mapper.UserMapper;
import com.ahmed.chatflow.repository.RefreshTokenRepository;
import com.ahmed.chatflow.repository.UserRepository;
import com.ahmed.chatflow.security.CustomUserDetailsService;
import com.ahmed.chatflow.security.JwtService;
import com.ahmed.chatflow.security.TokenHasher;
import com.ahmed.chatflow.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       JwtService jwtService, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = CustomUserDetailsService.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Un compte avec cet email existe déjà");
        }
        Instant now = Instant.now();
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setRole(Role.USER);
        user.setCreatedAt(now);
        User saved = userRepository.save(user);
        return issueTokens(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = CustomUserDetailsService.normalize(request.email());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new UnauthorizedException("Identifiants invalides"));
            if (!user.isEnabled()) {
                throw new UnauthorizedException("Ce compte est désactivé");
            }
            return issueTokens(user);
        } catch (AuthenticationException exception) {
            throw new UnauthorizedException("Identifiants invalides");
        }
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenHasher.hash(request.refreshToken()))
                .orElseThrow(() -> new UnauthorizedException("Jeton de rafraîchissement invalide"));
        Instant now = Instant.now();
        if (stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(now)) {
            throw new UnauthorizedException("Jeton de rafraîchissement invalide");
        }
        User user = stored.getUser();
        if (!user.isEnabled()) {
            throw new UnauthorizedException("Ce compte est désactivé");
        }
        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);
        return issueTokens(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByTokenHash(TokenHasher.hash(request.refreshToken()))
                .ifPresent(token -> {
                    if (token.getRevokedAt() == null) {
                        token.setRevokedAt(Instant.now());
                        refreshTokenRepository.save(token);
                    }
                });
    }

    private AuthResponse issueTokens(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = TokenHasher.generateRefreshToken();
        Instant now = Instant.now();
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash(TokenHasher.hash(refreshToken));
        stored.setCreatedAt(now);
        stored.setExpiresAt(now.plus(jwtService.getRefreshExpiration()));
        refreshTokenRepository.save(stored);
        return new AuthResponse(accessToken, refreshToken, "Bearer",
                jwtService.getAccessExpirationSeconds(), userMapper.toDto(user));
    }
}
