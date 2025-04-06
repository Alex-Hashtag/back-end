package org.acs.stuco.backend.auth;

import org.acs.stuco.backend.auth.dto.LoginRequest;
import org.acs.stuco.backend.auth.dto.UserResponse;
import org.acs.stuco.backend.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/auth")
public class AuthController
{
    private final AuthService authService;

    public AuthController(AuthService authService)
    {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestParam("email") String email,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("password") String password,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar
    )
    {
        // Combine the two name fields into a single string
        String fullName = firstName + " " + lastName;

        // Delegate to AuthService
        return authService.register(email, fullName, password, avatar);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request)
    {
        return authService.login(request);
    }

    @GetMapping("/verify/{token}")
    public ResponseEntity<String> verifyEmail(@PathVariable String token)
    {
        return authService.verifyEmail(token);
    }


    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user)
    {
        return ResponseEntity.ok(new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRole().ordinal(),
                user.isEmailVerified()   // Include email verification status
        ));
    }

}

