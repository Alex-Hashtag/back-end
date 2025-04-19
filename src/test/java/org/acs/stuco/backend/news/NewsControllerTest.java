package org.acs.stuco.backend.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acs.stuco.backend.user.Role;
import org.acs.stuco.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(locations = "classpath:application-test.properties")
class NewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NewsService newsService;

    private NewsPost testNewsPost;
    private NewsPostRequest testNewsPostRequest;
    private UserDetails stucoUserDetails;
    private UserDetails regularUserDetails;
    private final Long testId = 1L;

    @BeforeEach
    void setUp() {
        testNewsPost = new NewsPost();
        testNewsPost.setId(testId);
        testNewsPost.setTitle("Test News");
        testNewsPost.setContent("Test content for news post");
        testNewsPost.setBannerPhotoUrl("banner.jpg");
        testNewsPost.setExtraPhotos("extra1.jpg,extra2.jpg");
        testNewsPost.setCreatedAt(LocalDateTime.now());

        testNewsPostRequest = new NewsPostRequest(
                "Test News",
                "Test content for news post",
                "banner.jpg",
                "extra1.jpg,extra2.jpg"
        );

        // Create UserDetails implementations for Spring Security tests
        stucoUserDetails = org.springframework.security.core.userdetails.User.builder()
            .username("stuco@acsbg.org")
            .password("password")
            .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUCO")))
            .build();

        regularUserDetails = org.springframework.security.core.userdetails.User.builder()
            .username("regular@acsbg.org")
            .password("password")
            .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
            .build();
    }

    @Test
    void getAllNews_ShouldReturnPageOfNews() throws Exception {
        // Arrange
        Page<NewsPost> mockPage = new PageImpl<>(List.of(testNewsPost));
        when(newsService.findAll(any(Pageable.class))).thenReturn(mockPage);

        // Act & Assert
        mockMvc.perform(get("/api/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(testNewsPost.getId()))
                .andExpect(jsonPath("$.content[0].title").value(testNewsPost.getTitle()))
                .andExpect(jsonPath("$.content[0].content").value(testNewsPost.getContent()));

        verify(newsService).findAll(any(Pageable.class));
    }

    @Test
    void getNewsById_WhenNewsPostExists_ShouldReturnNewsPost() throws Exception {
        // Arrange
        when(newsService.findById(testId)).thenReturn(testNewsPost);

        // Act & Assert
        mockMvc.perform(get("/api/news/{id}", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testNewsPost.getId()))
                .andExpect(jsonPath("$.title").value(testNewsPost.getTitle()))
                .andExpect(jsonPath("$.content").value(testNewsPost.getContent()));

        verify(newsService).findById(testId);
    }

    @Test
    void getNewsById_WhenNewsPostDoesNotExist_ShouldReturnNotFound() throws Exception {
        // Arrange
        when(newsService.findById(testId)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/news/{id}", testId))
                .andExpect(status().isNotFound());

        verify(newsService).findById(testId);
    }
}
