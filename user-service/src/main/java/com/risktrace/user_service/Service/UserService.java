package com.risktrace.user_service.Service;

import com.risktrace.user_service.DTO.*;
import com.risktrace.user_service.Enums.Role;
import com.risktrace.user_service.Exception.AccountNotVerifiedException;
import com.risktrace.user_service.Exception.EmailUnverifiedPendingException;
import com.risktrace.user_service.Exception.InvalidTokenException;
import com.risktrace.user_service.Model.PasswordResetToken;
import com.risktrace.user_service.Model.User;
import com.risktrace.user_service.Model.VerificationToken;
import com.risktrace.user_service.Repository.PasswordResetTokenRepository;
import com.risktrace.user_service.Repository.UserRepository;
import com.risktrace.user_service.Repository.VerificationTokenRepository;
import com.risktrace.user_service.Security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final VerificationTokenRepository verificationTokenRepo;
    private final PasswordResetTokenRepository passwordResetTokenRepo;
    private final EmailService emailService;

    @Value("${app.verification-token-expiry-hours:24}")
    private long verificationTokenExpiryHours;

    @Value("${app.password-reset-token-expiry-hours:1}")
    private long passwordResetTokenExpiryHours;

    // =========================================================================
    // REGISTRATION (enabled = false until email verified)
    // =========================================================================

    @Transactional
    public UserResponse register(RegisterRequest request) {
        var existingUser = repository.findByEmail(request.getEmail());
        if (existingUser.isPresent()) {
            if (!existingUser.get().isEnabled()) {
                // Account exists but not verified — inform them a verification email was
                // already sent
                throw new EmailUnverifiedPendingException(
                        "A verification email has already been sent to this address. Please check your inbox (or spam folder).");
            }
            throw new RuntimeException("Email already exists");
        }

        var user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ANALYST)
                .enabled(false) // Must verify email first
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        var savedUser = repository.save(user);

        // Generate & send verification email
        String token = createVerificationToken(savedUser.getEmail());
        emailService.sendVerificationEmail(savedUser.getEmail(), savedUser.getFullName(), token);

        return mapToUserResponse(savedUser);
    }

    // =========================================================================
    // LOGIN (blocked if not verified)
    // =========================================================================

    public AuthResponse authenticate(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (DisabledException e) {
            throw new AccountNotVerifiedException("ACCOUNT_NOT_VERIFIED");
        }

        var user = repository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Extra safety check (should never reach here since Spring Security checks
        // enabled)
        if (!user.isEnabled()) {
            throw new AccountNotVerifiedException("ACCOUNT_NOT_VERIFIED");
        }

        var jwtToken = jwtUtils.generateToken(user);

        return AuthResponse.builder()
                .token(jwtToken)
                .role(user.getRole() != null ? user.getRole().name() : Role.ANALYST.name())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .id(user.getId())
                .build();
    }

    // =========================================================================
    // EMAIL VERIFICATION
    // =========================================================================

    @Transactional
    public void verifyEmail(String token) {
        VerificationToken vt = verificationTokenRepo.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification token"));

        if (Instant.now().isAfter(vt.getExpiryDate())) {
            verificationTokenRepo.delete(vt);
            throw new InvalidTokenException("Verification link has expired. Please request a new one.");
        }

        var user = repository.findByEmail(vt.getUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        user.setEnabled(true);
        user.setUpdatedAt(Instant.now());
        repository.save(user);

        verificationTokenRepo.delete(vt);
        log.info("Email verified for user: {}", user.getEmail());
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        var user = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.isEnabled()) {
            throw new RuntimeException("Account is already verified");
        }

        // Remove old tokens for this user
        verificationTokenRepo.deleteByUserEmail(email);

        String token = createVerificationToken(email);
        emailService.sendVerificationEmail(email, user.getFullName(), token);
    }

    // =========================================================================
    // FORGOT / RESET PASSWORD
    // =========================================================================

    @Transactional
    public void forgotPassword(String email) {
        // Do NOT reveal whether email exists (security best practice)
        repository.findByEmail(email).ifPresent(user -> {
            // Remove any existing reset tokens
            passwordResetTokenRepo.deleteByUserEmail(email);

            String token = createPasswordResetToken(email);
            emailService.sendPasswordResetEmail(email, user.getFullName(), token);
            log.info("Password reset email sent to: {}", email);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken prt = passwordResetTokenRepo.findByToken(request.getToken())
                .orElseThrow(() -> new InvalidTokenException("Invalid or already used reset token"));

        if (Instant.now().isAfter(prt.getExpiryDate())) {
            passwordResetTokenRepo.delete(prt);
            throw new InvalidTokenException("Password reset link has expired. Please request a new one.");
        }

        var user = repository.findByEmail(prt.getUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(Instant.now());
        repository.save(user);

        // Invalidate the token immediately after use
        passwordResetTokenRepo.delete(prt);
        log.info("Password reset successfully for user: {}", user.getEmail());
    }

    // =========================================================================
    // ADMIN OPERATIONS
    // =========================================================================

    public List<UserResponse> getAllUsers() {
        return repository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(String id) {
        return repository.findById(id)
                .map(this::mapToUserResponse)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public UserResponse updateUser(String id, UpdateUserRequest request) {
        var user = repository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        user.setUpdatedAt(Instant.now());
        return mapToUserResponse(repository.save(user));
    }

    public void deleteUser(String id) {
        if (!repository.existsById(id)) {
            throw new UsernameNotFoundException("User not found");
        }
        repository.deleteById(id);
    }

    // =========================================================================
    // PROFILE MANAGEMENT
    // =========================================================================

    public UserResponse getProfile(String email) {
        var user = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        var user = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setUpdatedAt(Instant.now());
        return mapToUserResponse(repository.save(user));
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private String createVerificationToken(String userEmail) {
        String token = UUID.randomUUID().toString();
        var vt = VerificationToken.builder()
                .token(token)
                .userEmail(userEmail)
                .expiryDate(Instant.now().plus(verificationTokenExpiryHours, ChronoUnit.HOURS))
                .createdAt(Instant.now())
                .build();
        verificationTokenRepo.save(vt);
        return token;
    }

    private String createPasswordResetToken(String userEmail) {
        String token = UUID.randomUUID().toString();
        var prt = PasswordResetToken.builder()
                .token(token)
                .userEmail(userEmail)
                .expiryDate(Instant.now().plus(passwordResetTokenExpiryHours, ChronoUnit.HOURS))
                .createdAt(Instant.now())
                .build();
        passwordResetTokenRepo.save(prt);
        return token;
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole() : Role.ANALYST)
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
