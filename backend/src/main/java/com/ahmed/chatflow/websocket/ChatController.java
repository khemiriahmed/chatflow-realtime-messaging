package com.ahmed.chatflow.websocket;

import com.ahmed.chatflow.dto.ReadEvent;
import com.ahmed.chatflow.dto.SendMessageRequest;
import com.ahmed.chatflow.dto.TypingEvent;
import com.ahmed.chatflow.security.UserPrincipal;
import com.ahmed.chatflow.service.ConversationService;
import com.ahmed.chatflow.service.MessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(MessageService messageService, ConversationService conversationService,
                          SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void send(@Payload SendMessageRequest request, Principal principal) {
        messageService.send(userId(principal), request);
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingEvent event, Principal principal) {
        Long userId = userId(principal);
        conversationService.requireMembership(event.conversationId(), userId);
        messagingTemplate.convertAndSend("/topic/conversation/" + event.conversationId() + "/typing", event);
    }

    @MessageMapping("/chat.delivered")
    public void delivered(@Payload ReadEvent event, Principal principal) {
        messageService.markDelivered(userId(principal), event.conversationId(), event.messageId());
    }

    @MessageMapping("/chat.read")
    public void read(@Payload ReadEvent event, Principal principal) {
        messageService.markRead(userId(principal), event.conversationId(), event.messageId());
    }

    private Long userId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        throw new org.springframework.security.access.AccessDeniedException("Session STOMP non authentifiée");
    }
}
