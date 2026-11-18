package com.ahmed.chatflow.controller;

import com.ahmed.chatflow.dto.AddMemberRequest;
import com.ahmed.chatflow.dto.ConversationDto;
import com.ahmed.chatflow.dto.CreateConversationRequest;
import com.ahmed.chatflow.dto.MessageDto;
import com.ahmed.chatflow.dto.PageResponse;
import com.ahmed.chatflow.security.UserPrincipal;
import com.ahmed.chatflow.service.ConversationService;
import com.ahmed.chatflow.service.MessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    public ConversationController(ConversationService conversationService, MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    @GetMapping
    public PageResponse<ConversationDto> list(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return conversationService.list(principal.getId(), page, size);
    }

    @PostMapping
    public ResponseEntity<ConversationDto> create(@AuthenticationPrincipal UserPrincipal principal,
                                                  @Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.create(principal.getId(), request));
    }

    @GetMapping("/{id}")
    public ConversationDto get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return conversationService.get(principal.getId(), id);
    }

    @GetMapping("/{id}/messages")
    public PageResponse<MessageDto> history(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return messageService.history(principal.getId(), id, page, size);
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ConversationDto> addMember(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.addMember(principal.getId(), id, request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id, @PathVariable Long userId) {
        conversationService.removeMember(principal.getId(), id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        conversationService.leave(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id) {
        messageService.markConversationRead(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
