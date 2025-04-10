package org.acs.stuco.backend.auth;

import jakarta.validation.Valid;
import org.acs.stuco.backend.auth.dto.LoginRequest;
import org.acs.stuco.backend.auth.dto.RegisterRequest;
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
            @Valid @RequestPart("user") RegisterRequest request,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar
    )
    {
        String fullName = request.firstName() + " " + request.lastName();
        return authService.register(request.email(), fullName, request.password(), avatar);
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
                user.isEmailVerified()
        ));
    }

}


