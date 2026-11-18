package com.ahmed.chatflow.websocket;

import com.ahmed.chatflow.security.UserPrincipal;
import com.ahmed.chatflow.service.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class PresenceEventListener {

    private final PresenceService presenceService;

    public PresenceEventListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String sessionId = event.getMessage().getHeaders()
                .get(SimpMessageHeaderAccessor.SESSION_ID_HEADER, String.class);
        presenceService.connect(userId(event.getUser()), sessionId);
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        presenceService.disconnect(event.getSessionId(), userId(event.getUser()));
    }

    private Long userId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        return null;
    }
}
