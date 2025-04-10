package org.acs.stuco.backend.auth;

import org.acs.stuco.backend.auth.dto.LoginRequest;
import org.acs.stuco.backend.email.EmailClient;
import org.acs.stuco.backend.upload.UploadService;
import org.acs.stuco.backend.user.User;
import org.acs.stuco.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class AuthServiceTest
{

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailClient emailClient;

    @Mock
    private UploadService uploadService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp()
    {

    }

    @Test
    @DisplayName("Register: Should create new user and send verification email")
    void registerSuccess()
    {
        String email = "test@acsbg.org";
        String fullName = "John Doe";
        String password = "Password1";

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation ->
        {
            User saved = invocation.getArgument(0);
            saved.setId(123L);
            return saved;
        });
        when(uploadService.upload(any(MultipartFile.class))).thenReturn("avatarUrl");

        ResponseEntity<?> response = authService.register(email, fullName, password, mock(MultipartFile.class));
        assertThat(response.getStatusCodeValue()).isEqualTo(201);
        assertThat(response.getBody()).asString().contains("Registration successful");

        verify(userRepository).save(any(User.class));
        verify(emailClient, atLeastOnce()).sendEmail(
                anyList(), anyList(), anyList(), anyString(), anyString(), anyBoolean(), anyList()
        );
    }

    @Test
    @DisplayName("Register: Should fail if email not @acsbg.org")
    void registerWrongDomain()
    {
        ResponseEntity<?> response = authService.register(
                "user@wrongdomain.com",
                "John Doe",
                "Password1",
                null
        );
        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getBody()).asString().contains("Only @acsbg.org emails allowed");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Register: Should fail if email already registered")
    void registerEmailExists()
    {
        when(userRepository.existsByEmail("test@acsbg.org")).thenReturn(true);

        ResponseEntity<?> response = authService.register(
                "test@acsbg.org",
                "John Doe",
                "Password1",
                null
        );
        assertThat(response.getStatusCodeValue()).isEqualTo(409);
        assertThat(response.getBody()).asString().contains("Email already registered");
    }

    @Test
    @DisplayName("Login: Should return token when successful and user verified")
    void loginSuccess()
    {
        String email = "verified@acsbg.org";
        String password = "Password1";

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("encoded");
        user.setEmailVerified(true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("mocked-jwt");

        ResponseEntity<?> response = authService.login(new LoginRequest(email, password));
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOfAny(java.util.Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsKey("token");
        assertThat(body.get("token")).isEqualTo("mocked-jwt");
    }

    @Test
    @DisplayName("Login: Should fail if credentials invalid")
    void loginInvalidCredentials()
    {
        when(userRepository.findByEmail("invalid@acsbg.org")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("invalid@acsbg.org", "pw")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("Login: Should fail if user not verified")
    void loginNotVerified()
    {
        User user = new User();
        user.setEmail("unverified@acsbg.org");
        user.setPasswordHash("hashed");
        user.setEmailVerified(false);

        when(userRepository.findByEmail("unverified@acsbg.org")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);

        ResponseEntity<?> response = authService.login(new LoginRequest("unverified@acsbg.org", "pw"));
        assertThat(response.getStatusCodeValue()).isEqualTo(403);
        assertThat(response.getBody()).asString().contains("Please verify your email");
    }

    @Test
    @DisplayName("verifyEmail: Should set user verified and publish event")
    void verifyEmailSuccess()
    {
        String token = UUID.randomUUID().toString();
        User user = new User();
        user.setVerificationToken(token);

        when(userRepository.findByVerificationToken(token)).thenReturn(Optional.of(user));

        ResponseEntity<String> response = authService.verifyEmail(token);
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).contains("Email verified successfully");

        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("verifyEmail: Should fail if token invalid")
    void verifyEmailFail()
    {
        when(userRepository.findByVerificationToken("badToken")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("badToken"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid token");
    }

    @Test
    @DisplayName("sendVerificationEmailAsync: Should handle email failures gracefully")
    void sendVerificationAsyncFail() throws Exception
    {
        doThrow(new RuntimeException("Email service down"))
                .when(emailClient).sendEmail(anyList(), anyList(), anyList(), anyString(), anyString(), anyBoolean(), anyList());

        CompletableFuture<Void> future = authService.sendVerificationEmailAsync("test@acsbg.org", "token");
        assertThat(future)
                .failsWithin(Duration.ofMillis(2000))
                .withThrowableOfType(Throwable.class)
                .withMessageContaining("Email service down");
    }
}

