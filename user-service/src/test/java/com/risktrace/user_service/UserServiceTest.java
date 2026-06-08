package com.risktrace.user_service;

import com.risktrace.user_service.DTO.LoginRequest;
import com.risktrace.user_service.DTO.RegisterRequest;
import com.risktrace.user_service.DTO.UserResponse;
import com.risktrace.user_service.DTO.AuthResponse;
import com.risktrace.user_service.Enums.Role;
import com.risktrace.user_service.Model.User;
import com.risktrace.user_service.Repository.UserRepository;
import com.risktrace.user_service.Security.JwtUtils;
import com.risktrace.user_service.Service.UserService;
import com.risktrace.user_service.Service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.risktrace.user_service.Repository.RefreshTokenRepository;
import com.risktrace.user_service.Repository.VerificationTokenRepository;
import com.risktrace.user_service.Repository.PasswordResetTokenRepository;
import com.risktrace.user_service.Repository.BlacklistedTokenRepository;
import com.risktrace.user_service.Repository.OrganizationMemberRepository;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import com.risktrace.user_service.Model.VerificationToken;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private EmailService emailService;

    @Mock
    private VerificationTokenRepository verificationTokenRepo;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepo;

    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private SecretGenerator secretGenerator;

    @Mock
    private QrGenerator qrGenerator;

    @Mock
    private CodeVerifier codeVerifier;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRegister_Success() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "password");
        User user = User.builder()
                .id("1")
                .fullName("Test User")
                .email("test@example.com")
                .role(Role.USER)
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(verificationTokenRepo.save(any(VerificationToken.class))).thenReturn(new VerificationToken());

        UserResponse response = userService.register(request);

        assertNotNull(response);
        assertEquals("test@example.com", response.getEmail());
        assertEquals(Role.USER, response.getRole());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testRegister_EmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "password");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(new User()));

        assertThrows(RuntimeException.class, () -> userService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testAuthenticate_Success() {
        LoginRequest request = new LoginRequest("test@example.com", "password");
        User user = User.builder()
                .id("1")
                .fullName("Test User")
                .email("test@example.com")
                .role(Role.USER)
                .emailVerified(true)
                .accountNonLocked(true)
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken(any(User.class))).thenReturn("jwtToken");

        AuthResponse response = userService.authenticate(request);

        assertNotNull(response);
        assertEquals("jwtToken", response.getToken());
        assertEquals("test@example.com", response.getEmail());
    }

    @Test
    void testAuthenticate_UserNotFound() {
        LoginRequest request = new LoginRequest("notfound@example.com", "password");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.authenticate(request));
    }
}
