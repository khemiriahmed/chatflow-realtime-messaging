package com.ahmed.chatflow.service;

import com.ahmed.chatflow.dto.AuthResponse;
import com.ahmed.chatflow.dto.LoginRequest;
import com.ahmed.chatflow.dto.LogoutRequest;
import com.ahmed.chatflow.dto.RefreshRequest;
import com.ahmed.chatflow.dto.RegisterRequest;
import com.ahmed.chatflow.dto.UserDto;
import com.ahmed.chatflow.entity.RefreshToken;
import com.ahmed.chatflow.entity.Role;
import com.ahmed.chatflow.entity.User;
import com.ahmed.chatflow.exception.ConflictException;
import com.ahmed.chatflow.exception.UnauthorizedException;
import com.ahmed.chatflow.mapper.UserMapper;
import com.ahmed.chatflow.repository.RefreshTokenRepository;
import com.ahmed.chatflow.repository.UserRepository;
import com.ahmed.chatflow.security.JwtService;
import com.ahmed.chatflow.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserMapper userMapper;
    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole(Role.USER);
        user.setEnabled(true);
    }

    @Test
    void registerNormalizesEmailHashesPasswordAndStoresHashedRefreshToken() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.getRefreshExpiration()).thenReturn(Duration.ofDays(7));
        when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);
        when(userMapper.toDto(any())).thenReturn(new UserDto(1L, "Alice", "alice@example.com",
                "USER", true, false, null));

        AuthResponse response = authService.register(
                new RegisterRequest("Alice", "Alice@Example.com", "password123"));

        assertEquals("access-token", response.accessToken());
        assertEquals(900L, response.expiresIn());
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getEmail().equals("alice@example.com")
                        && saved.getPasswordHash().equals("hashed-password")));
        verify(refreshTokenRepository).save(org.mockito.ArgumentMatchers.argThat(token ->
                token.getTokenHash().matches("[0-9a-f]{64}")
                        && token.getRevokedAt() == null
                        && token.getExpiresAt().isAfter(Instant.now())));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(
                new RegisterRequest("Alice", "alice@example.com", "password123")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginRejectsInvalidCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThrows(UnauthorizedException.class, () -> authService.login(
                new LoginRequest("alice@example.com", "wrong-password")));
    }

    @Test
    void refreshRotatesTokenAndRejectsReuse() {
        RefreshToken stored = storedRefreshToken();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(any())).thenReturn("new-access");
        when(jwtService.getRefreshExpiration()).thenReturn(Duration.ofDays(7));
        when(userMapper.toDto(any())).thenReturn(new UserDto(1L, "Alice", "alice@example.com",
                "USER", true, false, null));

        AuthResponse first = authService.refresh(new RefreshRequest("raw-refresh-token"));

        assertEquals("new-access", first.accessToken());
        assertNotNull(stored.getRevokedAt());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));

        assertThrows(UnauthorizedException.class,
                () -> authService.refresh(new RefreshRequest("raw-refresh-token")));
    }

    @Test
    void refreshRejectsDisabledUser() {
        user.setEnabled(false);
        RefreshToken stored = storedRefreshToken();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThrows(UnauthorizedException.class,
                () -> authService.refresh(new RefreshRequest("raw-refresh-token")));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void logoutRevokesStoredToken() {
        RefreshToken stored = storedRefreshToken();
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash("raw-refresh-token")))
                .thenReturn(Optional.of(stored));

        authService.logout(new LogoutRequest("raw-refresh-token"));

        assertNotNull(stored.getRevokedAt());
        verify(refreshTokenRepository).save(stored);
    }

    private RefreshToken storedRefreshToken() {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenHasher.hash("raw-refresh-token"));
        token.setCreatedAt(Instant.now().minusSeconds(60));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        return token;
    }
}
