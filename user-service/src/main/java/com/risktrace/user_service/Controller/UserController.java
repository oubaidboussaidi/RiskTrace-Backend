package com.risktrace.user_service.Controller;

import com.risktrace.user_service.DTO.*;
import com.risktrace.user_service.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ADMIN Endpoints
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable String id, @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        String requesterId = userService.getProfile(authentication.getName()).getId();
        if (requesterId.equals(id)) {
            return ResponseEntity.status(403).body(Map.of("error", "You cannot modify your own account from the admin panel."));
        }
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable String id, Authentication authentication) {
        String requesterId = userService.getProfile(authentication.getName()).getId();
        if (requesterId.equals(id)) {
            return ResponseEntity.status(403).body(Map.of("error", "You cannot delete your own account from the admin panel."));
        }
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // PROFILE Endpoints
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(Authentication authentication) {
        return ResponseEntity.ok(userService.getProfile(authentication.getName()));
    }

    // @PutMapping("/profile")
    // public ResponseEntity<UserResponse> updateProfile(Authentication
    // authentication,
    // @RequestBody UpdateProfileRequest request) {
    // return ResponseEntity.ok(userService.updateProfile(authentication.getName(),
    // request));
    // }

    // Update full name (no password required)
    @PutMapping("/profile/fullname")
    public ResponseEntity<UserResponse> updateFullName(
            Authentication authentication,
            @RequestBody UpdateFullNameRequest request) {
        return ResponseEntity.ok(userService.updateFullName(authentication.getName(), request));
    }

    // Change password (requires current password)
    @PostMapping("/profile/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    // 2FA Endpoints
    @GetMapping("/profile/2fa/setup")
    public ResponseEntity<Setup2FaResponse> setup2fa(Authentication authentication) {
        return ResponseEntity.ok(userService.setup2fa(authentication.getName()));
    }

    @PostMapping("/profile/2fa/enable")
    public ResponseEntity<Map<String, String>> enable2fa(Authentication authentication, @RequestBody Enable2FaRequest request) {
        userService.enable2fa(authentication.getName(), request);
        return ResponseEntity.ok(Map.of("message", "Two-factor authentication enabled successfully"));
    }

    @PostMapping("/profile/2fa/disable")
    public ResponseEntity<Map<String, String>> disable2fa(Authentication authentication, @RequestBody Map<String, String> request) {
        userService.disable2fa(authentication.getName(), request.get("currentPassword"));
        return ResponseEntity.ok(Map.of("message", "Two-factor authentication disabled successfully"));
    }
}
