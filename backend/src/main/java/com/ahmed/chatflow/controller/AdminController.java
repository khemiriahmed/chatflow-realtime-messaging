package com.ahmed.chatflow.controller;

import com.ahmed.chatflow.dto.AdminStatsDto;
import com.ahmed.chatflow.dto.PageResponse;
import com.ahmed.chatflow.dto.UserDto;
import com.ahmed.chatflow.entity.Role;
import com.ahmed.chatflow.exception.BadRequestException;
import com.ahmed.chatflow.service.AdminService;
import com.ahmed.chatflow.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@Validated
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;
    private final AdminService adminService;

    public AdminController(UserService userService, AdminService adminService) {
        this.userService = userService;
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public PageResponse<UserDto> users(@RequestParam(defaultValue = "") String query,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return userService.search(query, page, size);
    }

    @PatchMapping("/users/{id}/status")
    public UserDto setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return userService.setEnabled(id, enabled);
    }

    @PatchMapping("/users/{id}/role")
    public UserDto setRole(@PathVariable Long id, @RequestParam String role) {
        try {
            return userService.setRole(id, Role.valueOf(role.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Le rôle demandé est invalide");
        }
    }

    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return adminService.stats();
    }
}
