package com.ahmed.chatflow.mapper;

import com.ahmed.chatflow.dto.UserDto;
import com.ahmed.chatflow.entity.User;
import com.ahmed.chatflow.service.PresenceService;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    private final PresenceService presenceService;

    public UserMapper(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    public UserDto toDto(User user) {
        if (user == null) {
            return null;
        }
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole().name(),
                user.isEnabled(), presenceService.isOnline(user.getId()), user.getLastSeenAt());
    }
}
