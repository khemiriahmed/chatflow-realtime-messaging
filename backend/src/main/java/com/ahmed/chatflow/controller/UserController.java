package com.ahmed.chatflow.controller;

import com.ahmed.chatflow.dto.ChangePasswordRequest;
import com.ahmed.chatflow.dto.PageResponse;
import com.ahmed.chatflow.dto.UpdateProfileRequest;
import com.ahmed.chatflow.dto.UserDto;
import com.ahmed.chatflow.security.UserPrincipal;
import com.ahmed.chatflow.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getMe(principal.getId());
    }

    @PatchMapping("/me")
    public UserDto updateMe(@AuthenticationPrincipal UserPrincipal principal,
                            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateMe(principal.getId(), request);
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public PageResponse<UserDto> search(@RequestParam(defaultValue = "") String query,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return userService.search(query, page, size);
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable Long id) {
        return userService.getUser(id);
    }
}
