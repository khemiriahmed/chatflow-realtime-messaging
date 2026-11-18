package com.ahmed.chatflow.service;

import com.ahmed.chatflow.dto.ChangePasswordRequest;
import com.ahmed.chatflow.dto.PageResponse;
import com.ahmed.chatflow.dto.UpdateProfileRequest;
import com.ahmed.chatflow.dto.UserDto;
import com.ahmed.chatflow.entity.Role;
import com.ahmed.chatflow.entity.User;
import com.ahmed.chatflow.exception.BadRequestException;
import com.ahmed.chatflow.exception.ResourceNotFoundException;
import com.ahmed.chatflow.mapper.UserMapper;
import com.ahmed.chatflow.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    public UserDto getMe(Long userId) {
        return userMapper.toDto(findUser(userId));
    }

    public UserDto getUser(Long userId) {
        return userMapper.toDto(findUser(userId));
    }

    @Transactional
    public UserDto updateMe(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.setName(request.name().trim());
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Le mot de passe actuel est incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    public PageResponse<UserDto> search(String query, int page, int size) {
        String normalizedQuery = query == null ? "" : query.trim();
        return PageResponse.of(userRepository.search(normalizedQuery, PageRequest.of(page, size)).map(userMapper::toDto));
    }

    public PageResponse<UserDto> listUsers(int page, int size) {
        return PageResponse.of(userRepository.findAll(PageRequest.of(page, size)).map(userMapper::toDto));
    }

    @Transactional
    public UserDto setEnabled(Long userId, boolean enabled) {
        User user = findUser(userId);
        user.setEnabled(enabled);
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto setRole(Long userId, Role role) {
        if (role == null) {
            throw new BadRequestException("Le rôle est obligatoire");
        }
        User user = findUser(userId);
        user.setRole(role);
        return userMapper.toDto(userRepository.save(user));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
