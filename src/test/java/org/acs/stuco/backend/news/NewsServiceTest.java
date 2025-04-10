package org.acs.stuco.backend.news;

import org.acs.stuco.backend.news.event.NewsPostCreatedEvent;
import org.acs.stuco.backend.upload.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class NewsServiceTest
{

    @Mock
    private NewsRepository newsRepository;

    @Mock
    private UploadService uploadService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NewsService newsService;

    private NewsPost testNewsPost;
    private NewsPostRequest validRequest;
    private MockMultipartFile testBanner;
    private MockMultipartFile[] testExtraPhotos;

    @BeforeEach
    void setUp()
    {

        testNewsPost = new NewsPost();
        testNewsPost.setId(1L);
        testNewsPost.setTitle("Test News Post");
        testNewsPost.setContent("This is a test news post content");
        testNewsPost.setBannerPhotoUrl("http://example.com/banner.jpg");
        testNewsPost.setExtraPhotos("http://example.com/extra1.jpg,http://example.com/extra2.jpg");
        testNewsPost.setCreatedAt(LocalDateTime.now());

        validRequest = new NewsPostRequest(
                "Test News Post",
                "This is a test news post content",
                "http://example.com/banner.jpg",
                "http://example.com/extra1.jpg,http://example.com/extra2.jpg"
        );

        testBanner = new MockMultipartFile(
                "banner",
                "banner.jpg",
                "image/jpeg",
                "test banner content".getBytes()
        );

        MockMultipartFile extraPhoto1 = new MockMultipartFile(
                "extraPhoto1",
                "extra1.jpg",
                "image/jpeg",
                "test extra photo 1 content".getBytes()
        );

        MockMultipartFile extraPhoto2 = new MockMultipartFile(
                "extraPhoto2",
                "extra2.jpg",
                "image/jpeg",
                "test extra photo 2 content".getBytes()
        );

        testExtraPhotos = new MockMultipartFile[]{extraPhoto1, extraPhoto2};
    }

    @Test
    @DisplayName("findAll should return paginated news posts")
    void findAllShouldReturnPaginatedPosts()
    {

        Pageable pageable = PageRequest.of(0, 10);
        List<NewsPost> posts = Collections.singletonList(testNewsPost);
        Page<NewsPost> postsPage = new PageImpl<>(posts);

        when(newsRepository.findAll(pageable)).thenReturn(postsPage);

        Page<NewsPost> result = newsService.findAll(pageable);

        assertThat(result).isEqualTo(postsPage);
        assertThat(result.getContent()).hasSize(1);
        verify(newsRepository).findAll(pageable);
    }

    @Test
    @DisplayName("findById should return news post when exists")
    void findByIdShouldReturnNewsPostWhenExists()
    {

        when(newsRepository.findById(1L)).thenReturn(Optional.of(testNewsPost));

        NewsPost result = newsService.findById(1L);

        assertThat(result).isEqualTo(testNewsPost);
        verify(newsRepository).findById(1L);
    }

    @Test
    @DisplayName("findById should return null when news post doesn't exist")
    void findByIdShouldReturnNullWhenNewsPostDoesNotExist()
    {

        when(newsRepository.findById(999L)).thenReturn(Optional.empty());

        NewsPost result = newsService.findById(999L);

        assertThat(result).isNull();
        verify(newsRepository).findById(999L);
    }

    @Test
    @DisplayName("createNewsPost should create and return news post with valid request")
    void createNewsPostShouldCreateAndReturnNewsPost()
    {

        when(uploadService.upload(any(MultipartFile.class)))
                .thenReturn("http://example.com/new-banner.jpg")
                .thenReturn("http://example.com/new-extra1.jpg")
                .thenReturn("http://example.com/new-extra2.jpg");

        when(newsRepository.save(any(NewsPost.class))).thenAnswer(invocation ->
        {
            NewsPost savedPost = invocation.getArgument(0);
            savedPost.setId(1L);
            return savedPost;
        });

        ResponseEntity<?> response = newsService.createNewsPost(validRequest, testBanner, testExtraPhotos);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isInstanceOf(NewsPost.class);

        NewsPost createdPost = (NewsPost) response.getBody();
        assertThat(createdPost.getTitle()).isEqualTo(validRequest.title());
        assertThat(createdPost.getContent()).isEqualTo(validRequest.content());
        assertThat(createdPost.getBannerPhotoUrl()).isEqualTo("http://example.com/new-banner.jpg");

        verify(uploadService, times(3)).upload(any(MultipartFile.class));
        verify(newsRepository).save(any(NewsPost.class));

        ArgumentCaptor<NewsPostCreatedEvent> eventCaptor = ArgumentCaptor.forClass(NewsPostCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().newsPost()).isEqualTo(createdPost);
    }

    @Test
    @DisplayName("createNewsPost should validate and reject invalid requests")
    void createNewsPostShouldValidateAndRejectInvalidRequests()
    {

        NewsPostRequest invalidRequest = new NewsPostRequest(
                "",
                "Content",
                null,
                null
        );

        ResponseEntity<?> response = newsService.createNewsPost(invalidRequest, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).contains("Title is required");

        invalidRequest = new NewsPostRequest(
                "Title",
                "",
                null,
                null
        );

        response = newsService.createNewsPost(invalidRequest, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).contains("Content is required");

        verify(newsRepository, never()).save(any(NewsPost.class));
    }

    @Test
    @DisplayName("updateNewsPost should update existing news post")
    void updateNewsPostShouldUpdateExistingNewsPost()
    {

        when(newsRepository.findById(1L)).thenReturn(Optional.of(testNewsPost));
        when(uploadService.upload(any(MultipartFile.class))).thenReturn("http://example.com/updated-banner.jpg");

        NewsPostRequest updateRequest = new NewsPostRequest(
                "Updated Title",
                "Updated Content",
                null,
                null
        );

        ResponseEntity<?> response = newsService.updateNewsPost(1L, updateRequest, testBanner, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(NewsPost.class);

        NewsPost updatedPost = (NewsPost) response.getBody();
        assertThat(updatedPost.getTitle()).isEqualTo("Updated Title");
        assertThat(updatedPost.getContent()).isEqualTo("Updated Content");
        assertThat(updatedPost.getBannerPhotoUrl()).isEqualTo("http://example.com/updated-banner.jpg");

        verify(uploadService).delete("http://example.com/banner.jpg");
        verify(uploadService).upload(any(MultipartFile.class));
        verify(newsRepository).save(testNewsPost);
    }

    @Test
    @DisplayName("updateNewsPost should handle banner removal")
    void updateNewsPostShouldHandleBannerRemoval()
    {

        when(newsRepository.findById(1L)).thenReturn(Optional.of(testNewsPost));

        NewsPostRequest updateRequest = new NewsPostRequest(
                "Updated Title",
                "Updated Content",
                "", // Empty banner URL indicates removal
                null
        );

        ResponseEntity<?> response = newsService.updateNewsPost(1L, updateRequest, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        NewsPost updatedPost = (NewsPost) response.getBody();
        assertThat(updatedPost.getBannerPhotoUrl()).isEmpty();

        verify(uploadService).delete("http://example.com/banner.jpg");
        verify(newsRepository).save(testNewsPost);
    }

    @Test
    @DisplayName("updateNewsPost should return not found for non-existent post")
    void updateNewsPostShouldReturnNotFoundForNonExistentPost()
    {

        when(newsRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = newsService.updateNewsPost(999L, validRequest, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(newsRepository, never()).save(any(NewsPost.class));
    }

    @Test
    @DisplayName("deleteNewsPost should delete news post and associated files")
    void deleteNewsPostShouldDeleteNewsPostAndAssociatedFiles()
    {

        when(newsRepository.findById(1L)).thenReturn(Optional.of(testNewsPost));

        boolean result = newsService.deleteNewsPost(1L);

        assertThat(result).isTrue();

        verify(uploadService).delete("http://example.com/banner.jpg");
        verify(uploadService).delete("http://example.com/extra1.jpg");
        verify(uploadService).delete("http://example.com/extra2.jpg");

        verify(newsRepository).delete(testNewsPost);
    }

    @Test
    @DisplayName("deleteNewsPost should return false for non-existent post")
    void deleteNewsPostShouldReturnFalseForNonExistentPost()
    {

        when(newsRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = newsService.deleteNewsPost(999L);

        assertThat(result).isFalse();
        verify(newsRepository, never()).delete(any(NewsPost.class));
    }
}

