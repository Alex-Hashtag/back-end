package org.acs.stuco.backend.news;

import org.acs.stuco.backend.news.event.NewsPostCreatedEvent;
import org.acs.stuco.backend.upload.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class NewsServiceTest {

    @Autowired
    private NewsService newsService;

    @MockitoBean
    private NewsRepository newsRepository;

    @MockitoBean
    private UploadService uploadService;

    @MockitoBean
    private ApplicationEventPublisher eventPublisher;

    private NewsPost testNewsPost;
    private NewsPostRequest testNewsPostRequest;
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
    }

    @Test
    void findAll_ShouldReturnPageOfNewsPosts() {
        // Arrange
        Page<NewsPost> mockPage = new PageImpl<>(List.of(testNewsPost));
        Pageable pageable = mock(Pageable.class);
        when(newsRepository.findAll(pageable)).thenReturn(mockPage);

        // Act
        Page<NewsPost> result = newsService.findAll(pageable);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals(testNewsPost, result.getContent().get(0));
        verify(newsRepository).findAll(pageable);
    }

    @Test
    void findById_WhenNewsPostExists_ShouldReturnNewsPost() {
        // Arrange
        when(newsRepository.findById(testId)).thenReturn(Optional.of(testNewsPost));

        // Act
        NewsPost result = newsService.findById(testId);

        // Assert
        assertNotNull(result);
        assertEquals(testNewsPost.getId(), result.getId());
        assertEquals(testNewsPost.getTitle(), result.getTitle());
        verify(newsRepository).findById(testId);
    }

    @Test
    void findById_WhenNewsPostDoesNotExist_ShouldReturnNull() {
        // Arrange
        when(newsRepository.findById(testId)).thenReturn(Optional.empty());

        // Act
        NewsPost result = newsService.findById(testId);

        // Assert
        assertNull(result);
        verify(newsRepository).findById(testId);
    }

    @Test
    void createNewsPost_WithValidRequest_ShouldCreateAndReturnNewsPost() {
        // Arrange
        when(uploadService.upload(any(MultipartFile.class))).thenReturn("uploaded-banner.jpg", "extra-photo1.jpg", "extra-photo2.jpg");
        when(newsRepository.save(any(NewsPost.class))).thenAnswer(invocation -> {
            NewsPost savedPost = invocation.getArgument(0);
            savedPost.setId(testId);
            return savedPost;
        });

        MultipartFile mockBanner = mock(MultipartFile.class);
        when(mockBanner.isEmpty()).thenReturn(false);

        MultipartFile mockExtra1 = mock(MultipartFile.class);
        when(mockExtra1.isEmpty()).thenReturn(false);

        MultipartFile mockExtra2 = mock(MultipartFile.class);
        when(mockExtra2.isEmpty()).thenReturn(false);

        MultipartFile[] extraPhotos = {mockExtra1, mockExtra2};

        // Act
        ResponseEntity<?> response = newsService.createNewsPost(testNewsPostRequest, mockBanner, extraPhotos);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        NewsPost createdPost = (NewsPost) response.getBody();
        
        assertNotNull(createdPost);
        assertEquals(testId, createdPost.getId());
        assertEquals(testNewsPost.getTitle(), createdPost.getTitle());
        assertEquals(testNewsPost.getContent(), createdPost.getContent());
        assertEquals("uploaded-banner.jpg", createdPost.getBannerPhotoUrl());
        assertTrue(createdPost.getExtraPhotos().contains("extra-photo1.jpg"));
        assertTrue(createdPost.getExtraPhotos().contains("extra-photo2.jpg"));
        
        verify(uploadService).upload(mockBanner);
        verify(uploadService).upload(mockExtra1);
        verify(uploadService).upload(mockExtra2);
        verify(newsRepository).save(any(NewsPost.class));
    }

    @Test
    void createNewsPost_WithMissingTitle_ShouldReturnBadRequest() {
        // Arrange
        NewsPostRequest invalidRequest = new NewsPostRequest(
                "", // Empty title
                "Test content",
                "banner.jpg",
                ""
        );

        // Act
        ResponseEntity<?> response = newsService.createNewsPost(invalidRequest, null, null);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("Title is required"));
        verify(newsRepository, never()).save(any(NewsPost.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createNewsPost_WithMissingContent_ShouldReturnBadRequest() {
        // Arrange
        NewsPostRequest invalidRequest = new NewsPostRequest(
                "Test Title",
                "", // Empty content
                "banner.jpg",
                ""
        );

        // Act
        ResponseEntity<?> response = newsService.createNewsPost(invalidRequest, null, null);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("Content is required"));
        verify(newsRepository, never()).save(any(NewsPost.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateNewsPost_WhenNewsPostExists_ShouldUpdateAndReturnNewsPost() {
        // Arrange
        when(newsRepository.findById(testId)).thenReturn(Optional.of(testNewsPost));
        when(newsRepository.save(any(NewsPost.class))).thenReturn(testNewsPost);
        
        NewsPostRequest updateRequest = new NewsPostRequest(
                "Updated Title",
                "Updated content",
                null,
                "extra1.jpg" // Keeping only one of the original photos
        );

        MultipartFile mockBanner = mock(MultipartFile.class);
        when(mockBanner.isEmpty()).thenReturn(false);
        when(uploadService.upload(mockBanner)).thenReturn("new-banner.jpg");

        // Act
        ResponseEntity<?> response = newsService.updateNewsPost(testId, updateRequest, mockBanner, null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        NewsPost updatedPost = (NewsPost) response.getBody();
        
        assertNotNull(updatedPost);
        assertEquals("Updated Title", updatedPost.getTitle());
        assertEquals("Updated content", updatedPost.getContent());
        assertEquals("new-banner.jpg", updatedPost.getBannerPhotoUrl());
        
        // Verify old banner was deleted
        verify(uploadService).delete("banner.jpg");
        
        // Verify extra photo that wasn't kept was deleted
        verify(uploadService).delete("extra2.jpg");
        
        verify(uploadService).upload(mockBanner);
        verify(newsRepository).save(testNewsPost);
    }

    @Test
    void updateNewsPost_WhenNewsPostDoesNotExist_ShouldReturnNotFound() {
        // Arrange
        when(newsRepository.findById(testId)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<?> response = newsService.updateNewsPost(testId, testNewsPostRequest, null, null);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(newsRepository, never()).save(any(NewsPost.class));
    }

    @Test
    void deleteNewsPost_WhenNewsPostExists_ShouldDeleteAndReturnTrue() {
        // Arrange
        when(newsRepository.findById(testId)).thenReturn(Optional.of(testNewsPost));
        doNothing().when(newsRepository).delete(any(NewsPost.class));
        
        // Act
        boolean result = newsService.deleteNewsPost(testId);

        // Assert
        assertTrue(result);
        
        // Verify banner and extra photos were deleted
        verify(uploadService).delete("banner.jpg");
        verify(uploadService).delete("extra1.jpg");
        verify(uploadService).delete("extra2.jpg");
        
        verify(newsRepository).delete(testNewsPost);
    }

    @Test
    void deleteNewsPost_WhenNewsPostDoesNotExist_ShouldReturnFalse() {
        // Arrange
        when(newsRepository.findById(testId)).thenReturn(Optional.empty());

        // Act
        boolean result = newsService.deleteNewsPost(testId);

        // Assert
        assertFalse(result);
        verify(newsRepository, never()).delete(any(NewsPost.class));
    }
}
