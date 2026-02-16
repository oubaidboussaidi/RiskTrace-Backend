package com.risktrace.user_service.Controller;

import com.risktrace.user_service.DTO.*;
import com.risktrace.user_service.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    // --- Registration ---
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    // --- Login ---
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.authenticate(request));
    }

    // --- Email Verification ---
    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        userService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
    }

    // --- Resend Verification Email ---
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@RequestBody ResendVerificationRequest request) {
        userService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Verification email sent. Please check your inbox."));
    }

    // --- Forgot Password ---
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request.getEmail());
        // Always return same response regardless of whether email exists (security)
        return ResponseEntity.ok(Map.of("message", "If that email exists, a password reset link has been sent."));
    }

    // --- Reset Password ---
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ResponseEntity
                .ok(Map.of("message", "Password reset successfully. You can now log in with your new password."));
    }
}
