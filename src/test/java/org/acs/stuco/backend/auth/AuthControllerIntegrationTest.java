package org.acs.stuco.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acs.stuco.backend.auth.dto.LoginRequest;
import org.acs.stuco.backend.auth.dto.RegisterRequest;
import org.acs.stuco.backend.user.Role;
import org.acs.stuco.backend.user.User;
import org.acs.stuco.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest
{

    private final String TEST_EMAIL = "testuser@acsbg.org";
    private final String TEST_PASSWORD = "Password123";
    private final String TEST_FIRST_NAME = "Test";
    private final String TEST_LAST_NAME = "User";
    private final String TEST_FULL_NAME = TEST_FIRST_NAME + " " + TEST_LAST_NAME;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp()
    {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> userRepository.delete(user));
    }

    private String getAuthHeaderForUser(User user)
    {
        return "Bearer " + jwtService.generateToken(user);
    }

    @Test
    @DisplayName("POST /register should create a new user with valid data")
    void registerShouldCreateNewUser() throws Exception
    {
        RegisterRequest request = new RegisterRequest(
                TEST_EMAIL,
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                TEST_PASSWORD
        );

        MockMultipartFile userFile = new MockMultipartFile(
                "user",
                "",
                "application/json",
                objectMapper.writeValueAsBytes(request)
        );

        ClassPathResource image = new ClassPathResource("test-avatar.png");

        MockMultipartFile avatarFile = new MockMultipartFile(
                "avatar",
                image.getFilename(),
                "image/png",
                image.getInputStream()
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/auth/register")
                        .file(userFile)
                        .file(avatarFile)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("Registration successful")));
    }


    @Test
    @DisplayName("POST /register should reject invalid email domain")
    void registerShouldRejectInvalidEmailDomain() throws Exception
    {
        RegisterRequest request = new RegisterRequest(
                "test@invalid.com",
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                TEST_PASSWORD
        );

        MockMultipartFile userFile = new MockMultipartFile(
                "user",
                "",
                "application/json",
                objectMapper.writeValueAsBytes(request)
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/auth/register")
                        .file(userFile))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Only @acsbg.org emails allowed")));
    }

    @Test
    @DisplayName("POST /register should reject if email already exists")
    void registerShouldRejectExistingEmail() throws Exception
    {

        User existingUser = new User();
        existingUser.setEmail(TEST_EMAIL);
        existingUser.setName(TEST_FULL_NAME);
        existingUser.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        userRepository.save(existingUser);

        RegisterRequest request = new RegisterRequest(
                TEST_EMAIL,
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                TEST_PASSWORD
        );

        MockMultipartFile userFile = new MockMultipartFile(
                "user",
                "",
                "application/json",
                objectMapper.writeValueAsBytes(request)
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/auth/register")
                        .file(userFile))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Email already registered")));
    }

    @Test
    @DisplayName("POST /login should return JWT with valid credentials")
    void loginShouldReturnJwtWithValidCredentials() throws Exception
    {

        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setName(TEST_FULL_NAME);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setEmailVerified(true);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        Map<String, String> responseMap = objectMapper.readValue(content, Map.class);
        assertThat(responseMap).containsKey("token");
        String token = responseMap.get("token");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("POST /login should reject with invalid credentials")
    void loginShouldRejectWithInvalidCredentials() throws Exception
    {
        LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, "wrongpassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /login should reject unverified users")
    void loginShouldRejectUnverifiedUsers() throws Exception
    {

        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setName(TEST_FULL_NAME);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setEmailVerified(false);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Please verify your email")));
    }

    @Test
    @DisplayName("GET /verify should verify user email with valid token")
    void verifyShouldActivateUserWithValidToken() throws Exception
    {

        String token = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setName(TEST_FULL_NAME);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setEmailVerified(false);
        user.setVerificationToken(token);
        userRepository.save(user);

        mockMvc.perform(get("/api/auth/verify/{token}", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email verified successfully")));

        User verifiedUser = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertThat(verifiedUser.isEmailVerified()).isTrue();
        assertThat(verifiedUser.getVerificationToken()).isNull();
    }

    @Test
    @DisplayName("GET /verify should reject invalid token")
    void verifyShouldRejectInvalidToken() throws Exception
    {
        mockMvc.perform(get("/api/auth/verify/{token}", "invalid-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /me should return user details for authenticated user")
    void meShouldReturnUserDetailsForAuthenticatedUser() throws Exception
    {

        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setName(TEST_FULL_NAME);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setEmailVerified(true);
        user.setRole(Role.USER);
        user.setAvatarUrl("http://example.com/avatar.jpg");
        userRepository.save(user);

        String authHeader = getAuthHeaderForUser(user);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.name").value(TEST_FULL_NAME))
                .andExpect(jsonPath("$.avatarUrl").value("http://example.com/avatar.jpg"))
                .andExpect(jsonPath("$.role").value(Role.USER.ordinal()))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

}

