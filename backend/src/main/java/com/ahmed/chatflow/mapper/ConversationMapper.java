package com.ahmed.chatflow.mapper;

import com.ahmed.chatflow.dto.ConversationDto;
import com.ahmed.chatflow.dto.MessageDto;
import com.ahmed.chatflow.dto.UserDto;
import com.ahmed.chatflow.entity.Conversation;
import com.ahmed.chatflow.entity.ConversationMember;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ConversationMapper {

    private final UserMapper userMapper;

    public ConversationMapper(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public ConversationDto toDto(Conversation conversation, MessageDto lastMessage, long unreadCount) {
        List<UserDto> members = conversation.getMembers().stream()
                .map(ConversationMember::getUser)
                .map(userMapper::toDto)
                .toList();
        Map<Long, String> memberRoles = conversation.getMembers().stream()
                .collect(Collectors.toMap(member -> member.getUser().getId(),
                        member -> member.getRole().name(), (first, second) -> first));
        return new ConversationDto(conversation.getId(), conversation.getType().name(), conversation.getTitle(),
                conversation.getCreatedAt(), conversation.getUpdatedAt(), members, memberRoles, lastMessage, unreadCount);
    }
}
