package org.acs.stuco.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acs.stuco.backend.auth.dto.*;
import org.acs.stuco.backend.user.Role;
import org.acs.stuco.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(locations = "classpath:application-test.properties")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    private User testUser;
    private UserDetails testUserDetails;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@acsbg.org");
        testUser.setName("Test User");
        testUser.setRole(Role.USER);
        testUser.setEmailVerified(true);
        testUser.setAvatarUrl("avatar.jpg");
        
        // Create a UserDetails implementation for Spring Security tests
        testUserDetails = org.springframework.security.core.userdetails.User.builder()
            .username(testUser.getEmail())
            .password("password")
            .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
            .build();
    }

    @Test
    void register_ShouldCallAuthService() throws Exception {
        // Arrange
        RegisterRequest registerRequest = new RegisterRequest(
            "test@acsbg.org",
            "Test",
            "User",
            "Password123"
        );
        
        MockMultipartFile userPart = new MockMultipartFile(
            "user", 
            "", 
            "application/json", 
            objectMapper.writeValueAsBytes(registerRequest)
        );
        
        MockMultipartFile avatar = new MockMultipartFile(
            "avatar", 
            "avatar.jpg", 
            "image/jpeg", 
            "test image content".getBytes()
        );
        
        ResponseEntity responseEntity = ResponseEntity.status(HttpStatus.CREATED).body("Registration successful!");
        when(authService.register(
            eq("test@acsbg.org"), 
            eq("Test User"), 
            eq("Password123"), 
            any()
        )).thenReturn(responseEntity);

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/auth/register")
                .file(userPart)
                .file(avatar))
                .andExpect(status().isCreated())
                .andExpect(content().string("Registration successful!"));

        verify(authService).register(
            eq("test@acsbg.org"), 
            eq("Test User"), 
            eq("Password123"), 
            any()
        );
    }

    @Test
    void login_ShouldCallAuthService() throws Exception {
        // Arrange
        LoginRequest loginRequest = new LoginRequest("test@acsbg.org", "Password123");
        
        ResponseEntity responseEntity = ResponseEntity.ok().body(Map.of("token", "test_token"));
        when(authService.login(any(LoginRequest.class)))
            .thenReturn(responseEntity);

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test_token"));
                
        // Verify that authService.login was called with the correct request
        ArgumentCaptor<LoginRequest> loginRequestCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(loginRequestCaptor.capture());
        
        LoginRequest capturedRequest = loginRequestCaptor.getValue();
        assertEquals("test@acsbg.org", capturedRequest.email());
        assertEquals("Password123", capturedRequest.password());
    }

    @Test
    void verifyEmail_ShouldCallAuthService() throws Exception {
        // Arrange
        String token = "verification_token";
        
        ResponseEntity responseEntity = ResponseEntity.ok().body("Email verified successfully!");
        when(authService.verifyEmail(token))
            .thenReturn(responseEntity);

        // Act & Assert
        mockMvc.perform(get("/api/auth/verify/{token}", token))
                .andExpect(status().isOk())
                .andExpect(content().string("Email verified successfully!"));
                
        verify(authService).verifyEmail(token);
    }

    @Test
    void forgotPassword_ShouldCallAuthService() throws Exception {
        // Arrange
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@acsbg.org");
        
        ResponseEntity responseEntity = ResponseEntity.ok().body("Password reset instructions sent to your email.");
        when(authService.forgotPassword("test@acsbg.org"))
            .thenReturn(responseEntity);

        // Act & Assert
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset instructions sent to your email."));
                
        verify(authService).forgotPassword("test@acsbg.org");
    }

    @Test
    void resetPassword_ShouldCallAuthService() throws Exception {
        // Arrange
        ResetPasswordRequest request = new ResetPasswordRequest("reset_token", "NewPassword123");
        
        ResponseEntity responseEntity = ResponseEntity.ok().body("Password has been reset successfully.");
        when(authService.resetPassword("reset_token", "NewPassword123"))
            .thenReturn(responseEntity);

        // Act & Assert
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password has been reset successfully."));
                
        verify(authService).resetPassword("reset_token", "NewPassword123");
    }

    @Test
    @WithMockUser
    void me_ShouldReturnUserDetails() throws Exception {
        // Setup authentication with our test user
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            testUser, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Act & Assert
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testUser.getId()))
            .andExpect(jsonPath("$.name").value(testUser.getName()))
            .andExpect(jsonPath("$.email").value(testUser.getEmail()))
            .andExpect(jsonPath("$.avatarUrl").value(testUser.getAvatarUrl()))
            .andExpect(jsonPath("$.role").value(testUser.getRole().ordinal()))
            .andExpect(jsonPath("$.emailVerified").value(testUser.isEmailVerified()));
            
        // Clean up
        SecurityContextHolder.clearContext();
    }
}
