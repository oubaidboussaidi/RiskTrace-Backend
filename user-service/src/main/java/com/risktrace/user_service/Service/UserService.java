package com.risktrace.user_service.Service;

import com.risktrace.user_service.DTO.*;
import com.risktrace.user_service.Enums.Role;
import com.risktrace.user_service.Enums.OrganizationRole;
import com.risktrace.user_service.Exception.AccountNotVerifiedException;
import com.risktrace.user_service.Exception.AccountLockedException;
import com.risktrace.user_service.Exception.EmailUnverifiedPendingException;
import com.risktrace.user_service.Exception.InvalidTokenException;
import com.risktrace.user_service.Model.BlacklistedToken;
import com.risktrace.user_service.Model.PasswordResetToken;
import com.risktrace.user_service.Model.RefreshToken;
import com.risktrace.user_service.Model.User;
import com.risktrace.user_service.Model.OrganizationMember;
import com.risktrace.user_service.Model.VerificationToken;
import com.risktrace.user_service.Repository.BlacklistedTokenRepository;
import com.risktrace.user_service.Repository.PasswordResetTokenRepository;
import com.risktrace.user_service.Repository.RefreshTokenRepository;
import com.risktrace.user_service.Repository.OrganizationMemberRepository;
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

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.util.Utils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
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
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final SecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;

    @Value("${app.verification-token-expiry-hours:24}")
    private long verificationTokenExpiryHours;

    @Value("${app.password-reset-token-expiry-hours:1}")
    private long passwordResetTokenExpiryHours;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION = 24 * 60 * 60 * 1000; // 24 hours

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
                .role(Role.USER)
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
        var user = repository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!user.isAccountNonLocked()) {
            if (unlockWhenTimeExpired(user)) {
                log.info("Account unlocked for user: {}", user.getEmail());
            } else {
                throw new AccountLockedException("Your account has been locked due to too many failed login attempts. Please try again later.");
            }
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (DisabledException e) {
            throw new AccountNotVerifiedException("ACCOUNT_NOT_VERIFIED");
        } catch (org.springframework.security.authentication.LockedException e) {
            throw new AccountLockedException("ACCOUNT_LOCKED");
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            increaseFailedAttempts(user);
            int remainingAttempts = MAX_FAILED_ATTEMPTS - user.getFailedLoginAttempts();
            if (remainingAttempts > 0) {
                throw new RuntimeException("Invalid credentials. " + remainingAttempts + " attempts remaining.");
            } else {
                throw new AccountLockedException("Your account has been locked due to too many failed login attempts.");
            }
        }

        if (user.getFailedLoginAttempts() > 0) {
            resetFailedAttempts(user.getEmail());
        }

        if (user.isTwoFactorEnabled()) {
            String mfaToken = jwtUtils.generateMfaToken(user.getEmail());
            return AuthResponse.builder()
                    .mfaRequired(true)
                    .mfaToken(mfaToken)
                    .email(user.getEmail())
                    .build();
        }

        var jwtToken = jwtUtils.generateToken(user);
        var refreshToken = createRefreshToken(user.getId());

        return AuthResponse.builder()
                .token(jwtToken)
                .refreshToken(refreshToken)
                .role(user.getRole() != null ? user.getRole().name() : Role.USER.name())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .id(user.getId())
                .build();
    }

    public AuthResponse verify2fa(Verify2FARequest request) {
        String email = jwtUtils.extractEmail(request.getMfaToken());
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        log.info("Verifying 2FA for user: {}. Code provided: {}", email, request.getCode());
        boolean isValid = codeVerifier.isValidCode(user.getTwoFactorSecret(), request.getCode());
        
        if (!isValid) {
            log.warn("Invalid 2FA code provided for user: {}. Current server time: {}", email, Instant.now());
            throw new IllegalArgumentException("Invalid verification code");
        }

        var jwtToken = jwtUtils.generateToken(user);
        var refreshToken = createRefreshToken(user.getId());

        return AuthResponse.builder()
                .token(jwtToken)
                .refreshToken(refreshToken)
                .role(user.getRole() != null ? user.getRole().name() : Role.USER.name())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .id(user.getId())
                .build();
    }

    public Setup2FaResponse setup2fa(String email) {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String secret = secretGenerator.generate();
        QrData data = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer("RiskTrace")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        String qrCodeBase64;
        try {
            byte[] qrCodeBytes = qrGenerator.generate(data);
            qrCodeBase64 = Utils.getDataUriForImage(qrCodeBytes, qrGenerator.getImageMimeType());
        } catch (QrGenerationException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }

        return Setup2FaResponse.builder()
                .secret(secret)
                .qrCodeImage(qrCodeBase64)
                .build();
    }

    @Transactional
    public void enable2fa(String email, Enable2FaRequest request) {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        log.info("Enabling 2FA for user: {}. Code provided: {}", email, request.getCode());
        boolean isValid = codeVerifier.isValidCode(request.getSecret(), request.getCode());
        
        if (!isValid) {
            log.warn("Invalid 2FA code during enablement for user: {}. Current server time: {}", email, Instant.now());
            throw new IllegalArgumentException("Invalid verification code");
        }

        user.setTwoFactorSecret(request.getSecret());
        user.setTwoFactorEnabled(true);
        repository.save(user);
    }

    @Transactional
    public void disable2fa(String email, String currentPassword) {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect current password");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        repository.save(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenStr) {
        // Find by hashed token
        String hashedToken = hashToken(refreshTokenStr);
        RefreshToken refreshToken = refreshTokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (refreshToken.getExpiryDate().before(new Date())) {
            refreshTokenRepository.delete(refreshToken);
            throw new InvalidTokenException("Refresh token expired");
        }

        User user = repository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Rotation: Delete old, create new
        refreshTokenRepository.delete(refreshToken);
        String newRefreshTokenStr = createRefreshToken(user.getId());
        String newAccessToken = jwtUtils.generateToken(user);

        return AuthResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshTokenStr)
                .role(user.getRole() != null ? user.getRole().name() : Role.USER.name())
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

    @Transactional
    public UserResponse updateUser(String id, UpdateUserRequest request) {
        log.info("updateUser called for id={} with request role='{}' enabled={}", id, request.getRole(), request.getEnabled());

        var user = repository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        log.info("User found: {} current role={}", user.getEmail(), user.getRole());

        if (request.getRole() != null && !request.getRole().isBlank()) {
            Role newRole = Role.fromString(request.getRole());
            log.info("Resolved new role: '{}' -> {}", request.getRole(), newRole);
            user.setRole(newRole);
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        user.setUpdatedAt(Instant.now());
        User saved = repository.save(user);
        log.info("Saved user {} with role={}", saved.getEmail(), saved.getRole());
        return mapToUserResponse(saved);
    }

    @Transactional
    public void deleteUser(String id) {
        if (!repository.existsById(id)) {
            throw new UsernameNotFoundException("User not found");
        }

        // Prevent deleting if user is the sole owner of any organization
        List<OrganizationMember> memberships = organizationMemberRepository.findByUserId(id);
        for (OrganizationMember membership : memberships) {
            if (membership.getRole() == OrganizationRole.OWNER) {
                long ownerCount = organizationMemberRepository.findByOrganizationId(membership.getOrganizationId())
                        .stream()
                        .filter(m -> m.getRole() == OrganizationRole.OWNER)
                        .count();
                if (ownerCount <= 1) {
                    throw new RuntimeException(
                            "Cannot delete user: They are the sole owner of an organization. Please transfer ownership or delete the organization first.");
                }
            }
        }

        // Remove from all organizations before user deletion
        organizationMemberRepository.deleteByUserId(id);
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

    // public UserResponse updateProfile(String email, UpdateProfileRequest request)
    // {
    // var user = repository.findByEmail(email)
    // .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    //
    // if (request.getFullName() != null && !request.getFullName().isBlank()) {
    // user.setFullName(request.getFullName());
    // }
    // if (request.getPassword() != null && !request.getPassword().isBlank()) {
    // user.setPassword(passwordEncoder.encode(request.getPassword()));
    // }
    // user.setUpdatedAt(Instant.now());
    // return mapToUserResponse(repository.save(user));
    // }

    @Transactional
    public UserResponse updateFullName(String email, UpdateFullNameRequest request) {
        var user = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
            user.setUpdatedAt(Instant.now());
            repository.save(user);

        }

        return mapToUserResponse(user);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        var user = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Verify current password
        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw new IllegalArgumentException("Current password is required");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Validate new password strength (optional)
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(Instant.now());
        repository.save(user);

        log.info("Password changed for user: {}", email);
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
                .role(user.getRole() != null ? user.getRole() : Role.USER)
                .enabled(user.isEnabled())
                .isTwoFactorEnabled(user.isTwoFactorEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        if (refreshTokenStr != null) {
            String hashedToken = hashToken(refreshTokenStr);
            refreshTokenRepository.findByToken(hashedToken).ifPresent(refreshTokenRepository::delete);
        }
        log.info("Successfully logged out");
    }

    private String createRefreshToken(String userId) {
        String token = UUID.randomUUID().toString();
        String hashedToken = hashToken(token);

        var refreshToken = RefreshToken.builder()
                .token(hashedToken)
                .userId(userId)
                .expiryDate(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)))
                .createdAt(Instant.now())
                .build();

        refreshTokenRepository.save(refreshToken);
        return token;
    }

    private String hashToken(String token) {
        // Using a simple SHA-256 or similar would be better than PasswordEncoder
        // because we need exact matches, not bcrypt's salted matches.
        // Actually, for tokens where we look them up by ID/Value, we need a
        // deterministic hash.
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }

    // =========================================================================
    // BRUTE FORCE PROTECTION HELPERS
    // =========================================================================

    private void increaseFailedAttempts(User user) {
        int newFailAttempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(newFailAttempts);

        if (newFailAttempts >= MAX_FAILED_ATTEMPTS) {
            lock(user);
        } else {
            repository.save(user);
        }
    }

    private void lock(User user) {
        user.setAccountNonLocked(false);
        user.setLockTime(Instant.now());
        repository.save(user);
    }

    private void resetFailedAttempts(String email) {
        repository.findByEmail(email).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            repository.save(user);
        });
    }

    private boolean unlockWhenTimeExpired(User user) {
        if (user.getLockTime() == null)
            return true;

        long lockTimeInMillis = user.getLockTime().toEpochMilli();
        long currentTimeInMillis = System.currentTimeMillis();

        if (lockTimeInMillis + LOCK_TIME_DURATION < currentTimeInMillis) {
            user.setAccountNonLocked(true);
            user.setLockTime(null);
            user.setFailedLoginAttempts(0);
            repository.save(user);
            return true;
        }

        return false;
    }
}
