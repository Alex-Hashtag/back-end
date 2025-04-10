package org.acs.stuco.backend.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acs.stuco.backend.auth.JwtService;
import org.acs.stuco.backend.user.Role;
import org.acs.stuco.backend.user.User;
import org.acs.stuco.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class NewsControllerIntegrationTest
{

    private final String REGULAR_USER_EMAIL = "newsuser@acsbg.org";
    private final String STUCO_USER_EMAIL = "newsstuco@acsbg.org";
    private final String ADMIN_USER_EMAIL = "newsadmin@acsbg.org";
    private final String TEST_PASSWORD = "Password123";
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NewsRepository newsRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    private User regularUser;
    private User stucoUser;
    private User adminUser;
    private NewsPost testNewsPost;

    @BeforeEach
    void setUp()
    {

        userRepository.findByEmail(REGULAR_USER_EMAIL).ifPresent(user -> userRepository.delete(user));
        userRepository.findByEmail(STUCO_USER_EMAIL).ifPresent(user -> userRepository.delete(user));
        userRepository.findByEmail(ADMIN_USER_EMAIL).ifPresent(user -> userRepository.delete(user));

        regularUser = createTestUser(REGULAR_USER_EMAIL, "News Regular User", Role.USER);
        stucoUser = createTestUser(STUCO_USER_EMAIL, "News Stuco Member", Role.STUCO);
        adminUser = createTestUser(ADMIN_USER_EMAIL, "News Admin User", Role.ADMIN);

        testNewsPost = new NewsPost();
        testNewsPost.setTitle("Test News Post");
        testNewsPost.setContent("This is a test news post content for integration testing.");
        testNewsPost.setCreatedAt(LocalDateTime.now());
        newsRepository.save(testNewsPost);
    }

    private User createTestUser(String email, String name, Role role)
    {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setRole(role);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private String getAuthHeaderForUser(User user)
    {
        return "Bearer " + jwtService.generateToken(user);
    }

    @Test
    @DisplayName("GET /api/news should return all news posts")
    void getAllNewsShouldReturnAllPosts() throws Exception
    {

        mockMvc.perform(get("/api/news")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").exists())
                .andExpect(jsonPath("$.content[0].content").exists());
    }

    @Test
    @DisplayName("GET /api/news/{id} should return specific news post")
    void getNewsByIdShouldReturnSpecificPost() throws Exception
    {

        mockMvc.perform(get("/api/news/{id}", testNewsPost.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testNewsPost.getId()))
                .andExpect(jsonPath("$.title").value(testNewsPost.getTitle()))
                .andExpect(jsonPath("$.content").value(testNewsPost.getContent()));
    }

    @Test
    @DisplayName("GET /api/news/{id} should return 404 for non-existent post")
    void getNewsByIdShouldReturn404ForNonExistentPost() throws Exception
    {
        mockMvc.perform(get("/api/news/{id}", 999L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/news should create news post when authorized")
    void createNewsPostShouldCreateWhenAuthorized() throws Exception
    {

        NewsPostRequest request = new NewsPostRequest(
                "Unauthorized Post Attempt",
                "This should be rejected",
                null,
                null
        );

        MockMultipartFile requestPart = new MockMultipartFile(
                "post",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/news")
                        .file(requestPart)
                        .header("Authorization", getAuthHeaderForUser(regularUser))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/news")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("POST /api/news should reject when unauthorized")
    void createNewsPostShouldRejectWhenUnauthorized() throws Exception
    {

        NewsPostRequest request = new NewsPostRequest(
                "Unauthorized Post",
                "This should be rejected",
                null,
                null
        );

        MockMultipartFile requestPart = new MockMultipartFile(
                "post",
                "post.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/news")
                        .file(requestPart)
                        .header("Authorization", getAuthHeaderForUser(regularUser))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());

        assertThat(newsRepository.findAll().stream()
                .anyMatch(post -> post.getTitle().equals("Unauthorized Post")))
                .isFalse();
    }

    @Test
    @DisplayName("PUT /api/news/{id} should update news post when authorized")
    void updateNewsPostShouldUpdateWhenAuthorized() throws Exception
    {

        NewsPostRequest request = new NewsPostRequest(
                "Updated Test Post",
                "This is updated content",
                null,
                null
        );

        MockMultipartFile requestPart = new MockMultipartFile(
                "post",
                "post.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/news/{id}", testNewsPost.getId())
                        .file(requestPart)
                        .header("Authorization", getAuthHeaderForUser(adminUser))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(r ->
                        {
                            r.setMethod("PUT");
                            return r;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Test Post"))
                .andExpect(jsonPath("$.content").value("This is updated content"));

        NewsPost updated = newsRepository.findById(testNewsPost.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Updated Test Post");
        assertThat(updated.getContent()).isEqualTo("This is updated content");
    }

    @Test
    @DisplayName("PUT /api/news/{id} should reject when unauthorized")
    void updateNewsPostShouldRejectWhenUnauthorized() throws Exception
    {

        NewsPostRequest request = new NewsPostRequest(
                "Unauthorized Update",
                "This should be rejected",
                null,
                null
        );

        MockMultipartFile requestPart = new MockMultipartFile(
                "post",
                "post.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/news/{id}", testNewsPost.getId())
                        .file(requestPart)
                        .header("Authorization", getAuthHeaderForUser(regularUser))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(r ->
                        {
                            r.setMethod("PUT");
                            return r;
                        }))
                .andExpect(status().isForbidden());

        NewsPost unchanged = newsRepository.findById(testNewsPost.getId()).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo(testNewsPost.getTitle());
        assertThat(unchanged.getContent()).isEqualTo(testNewsPost.getContent());
    }

    @Test
    @DisplayName("DELETE /api/news/{id} should delete news post when authorized")
    void deleteNewsPostShouldDeleteWhenAuthorized() throws Exception
    {

        mockMvc.perform(delete("/api/news/{id}", testNewsPost.getId())
                        .header("Authorization", getAuthHeaderForUser(stucoUser)))
                .andExpect(status().isOk());

        assertThat(newsRepository.findById(testNewsPost.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/news/{id} should reject when unauthorized")
    void deleteNewsPostShouldRejectWhenUnauthorized() throws Exception
    {

        mockMvc.perform(delete("/api/news/{id}", testNewsPost.getId())
                        .header("Authorization", getAuthHeaderForUser(regularUser)))
                .andExpect(status().isForbidden());

        assertThat(newsRepository.findById(testNewsPost.getId())).isPresent();
    }
}

