package org.acs.stuco.backend.auth;

import org.acs.stuco.backend.auth.dto.LoginRequest;
import org.acs.stuco.backend.auth.event.UserVerifiedEvent;
import org.acs.stuco.backend.email.EmailClient;
import org.acs.stuco.backend.upload.UploadService;
import org.acs.stuco.backend.user.User;
import org.acs.stuco.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


@Service
public class AuthService
{
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailClient emailClient;
    private final UploadService uploadService;
    private final ApplicationEventPublisher eventPublisher;
    @Value("${acs.service.frontend.url}")
    private String domain;

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            EmailClient emailClient,
            UploadService uploadService, ApplicationEventPublisher eventPublisher
    )
    {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.emailClient = emailClient;
        this.uploadService = uploadService;
        this.eventPublisher = eventPublisher;
    }

    public ResponseEntity<?> register(
            String email,
            String fullName,
            String password,
            MultipartFile avatar
    )
    {
        if (!email.endsWith("@acsbg.org"))
        {
            return ResponseEntity.badRequest().body("Only @acsbg.org emails allowed.");
        }

        if (userRepository.existsByEmail(email))
        {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already registered.");
        }

        if (!isPasswordValid(password))
        {
            return ResponseEntity.badRequest()
                    .body("Password must be at least 8 characters, contain an uppercase letter and a digit.");
        }

        if (fullName.trim().isEmpty())
        {
            return ResponseEntity.badRequest().body("Name cannot be empty.");
        }

        User user = new User();
        user.setEmail(email);
        user.setName(fullName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(false);

        if (avatar != null && !avatar.isEmpty())
        {
            String avatarUrl = uploadService.upload(avatar);
            user.setAvatarUrl(avatarUrl);
        }

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        userRepository.save(user);

        sendVerificationEmailAsync(email, token)
                .exceptionally(ex ->
                {
                    logger.error("Failed to send verification email to {}", email, ex);
                    return null;
                });

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Registration successful! Please check your email to verify.");
    }

    @Async
    public CompletableFuture<Void> sendVerificationEmailAsync(String email, String token)
    {
        try
        {
            String verificationLink = domain + "verify?token=" + token;
            emailClient.sendEmail(
                    List.of(email),
                    List.of(),
                    List.of(),
                    "Verify Your Email",
                    "Please click the following link to verify your email: " + verificationLink,
                    false,
                    List.of()
            );
            return CompletableFuture.completedFuture(null);
        } catch (Exception e)
        {
            logger.error("Critical: Failed to send verification email to {}", email, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public ResponseEntity<?> login(LoginRequest request)
    {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()))
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        if (!user.isEmailVerified())
        {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Please verify your email address before logging in.");
        }

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(Map.of("token", token));
    }

    public ResponseEntity<String> verifyEmail(String token)
    {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid token"));

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);

        eventPublisher.publishEvent(new UserVerifiedEvent(user));

        return ResponseEntity.ok("Email verified successfully!");
    }

    private boolean isPasswordValid(String password)
    {
        if (password.length() < 8) return false;
        if (!password.matches(".*[A-Z].*")) return false;
        return password.matches(".*\\d.*");
    }
}
