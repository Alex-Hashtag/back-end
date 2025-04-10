package org.acs.stuco.backend.news.event;

import org.acs.stuco.backend.email.EmailClient;
import org.acs.stuco.backend.news.NewsPost;
import org.acs.stuco.backend.user.User;
import org.acs.stuco.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class NewsPostCreatedEventListenerTest
{

    private static final String TEST_FRONTEND_URL = "https://test.example.com/";
    @Mock
    private EmailClient emailClient;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private NewsPostCreatedEventListener eventListener;
    private NewsPost testNewsPost;
    private User verifiedUser1;
    private User verifiedUser2;
    private NewsPostCreatedEvent testEvent;

    @BeforeEach
    void setUp()
    {

        testNewsPost = new NewsPost();
        testNewsPost.setId(1L);
        testNewsPost.setTitle("Test News Post");
        testNewsPost.setContent("This is the content of the test news post that's longer than 100 characters so we can test the preview functionality properly with truncation.");
        testNewsPost.setBannerPhotoUrl("http://example.com/banner.jpg");
        testNewsPost.setCreatedAt(LocalDateTime.now());

        verifiedUser1 = new User();
        verifiedUser1.setId(1L);
        verifiedUser1.setEmail("user1@acsbg.org");
        verifiedUser1.setEmailVerified(true);

        verifiedUser2 = new User();
        verifiedUser2.setId(2L);
        verifiedUser2.setEmail("user2@acsbg.org");
        verifiedUser2.setEmailVerified(true);

        testEvent = new NewsPostCreatedEvent(testNewsPost);

        ReflectionTestUtils.setField(eventListener, "frontendUrl", TEST_FRONTEND_URL);
    }

    @Test
    @DisplayName("Should send emails to all verified users when news post is created")
    void shouldSendEmailsToAllVerifiedUsers() throws ExecutionException, InterruptedException
    {

        when(userRepository.findByEmailVerified(true))
                .thenReturn(Arrays.asList(verifiedUser1, verifiedUser2));

        doNothing().when(emailClient).sendEmail(
                anyList(), anyList(), anyList(), anyString(), anyString(), anyBoolean(), anyList());

        CompletableFuture<Void> result = eventListener.handleNewsPostCreatedEvent(testEvent);
        result.get(); // Wait for completion

        verify(userRepository).findByEmailVerified(true);

        verify(emailClient).sendEmail(
                eq(Arrays.asList("user1@acsbg.org", "user2@acsbg.org")), // to
                eq(Collections.emptyList()), // cc
                eq(Collections.emptyList()), // bcc
                eq("New STUCO News: Test News Post"), // subject
                anyString(), // content
                eq(true), // isHtml
                eq(Collections.emptyList()) // attachments
        );
    }

    @Test
    @DisplayName("Should include content preview and banner in email")
    void shouldIncludeContentPreviewAndBannerInEmail() throws ExecutionException, InterruptedException
    {

        when(userRepository.findByEmailVerified(true))
                .thenReturn(Collections.singletonList(verifiedUser1));

        CompletableFuture<Void> result = eventListener.handleNewsPostCreatedEvent(testEvent);
        result.get(); // Wait for completion


        verify(emailClient).sendEmail(
                anyList(), anyList(), anyList(), anyString(),
                argThat(content ->
                        content.contains("This is the content of the test news post that's longer than 100 characters") &&
                                content.contains("...") &&
                                content.contains("Read more") &&
                                content.contains(TEST_FRONTEND_URL + "news/1") &&
                                content.contains("http://example.com/banner.jpg")
                ),
                eq(true),
                anyList()
        );
    }

    @Test
    @DisplayName("Should handle missing banner photo gracefully")
    void shouldHandleMissingBannerPhotoGracefully() throws ExecutionException, InterruptedException
    {

        when(userRepository.findByEmailVerified(true))
                .thenReturn(Collections.singletonList(verifiedUser1));

        testNewsPost.setBannerPhotoUrl(null);

        CompletableFuture<Void> result = eventListener.handleNewsPostCreatedEvent(testEvent);
        result.get(); // Wait for completion


        verify(emailClient).sendEmail(
                anyList(), anyList(), anyList(), anyString(),
                argThat(content -> !content.contains("img src=")),
                eq(true),
                anyList()
        );
    }

    @Test
    @DisplayName("Should not send emails when no verified users exist")
    void shouldNotSendEmailsWhenNoVerifiedUsersExist() throws ExecutionException, InterruptedException
    {

        when(userRepository.findByEmailVerified(true))
                .thenReturn(Collections.emptyList());

        CompletableFuture<Void> result = eventListener.handleNewsPostCreatedEvent(testEvent);
        result.get(); // Wait for completion

        verify(userRepository).findByEmailVerified(true);
        verify(emailClient, never()).sendEmail(
                anyList(), anyList(), anyList(), anyString(), anyString(), anyBoolean(), anyList());
    }

    @Test
    @DisplayName("Should handle email sending failures gracefully")
    void shouldHandleEmailSendingFailuresGracefully()
    {

        when(userRepository.findByEmailVerified(true))
                .thenReturn(Arrays.asList(verifiedUser1, verifiedUser2));

        doThrow(new RuntimeException("Email service error"))
                .when(emailClient).sendEmail(
                        anyList(), anyList(), anyList(), anyString(), anyString(), anyBoolean(), anyList());

        CompletableFuture<Void> future = eventListener.handleNewsPostCreatedEvent(testEvent);

        assertThat(future).isCompletedExceptionally();
    }

    @Test
    @DisplayName("Should handle double slashes in URL correctly")
    void shouldHandleDoubleSlashesInUrlCorrectly() throws ExecutionException, InterruptedException
    {

        when(userRepository.findByEmailVerified(true))
                .thenReturn(Collections.singletonList(verifiedUser1));

        ReflectionTestUtils.setField(eventListener, "frontendUrl", "https://test.example.com/");

        CompletableFuture<Void> result = eventListener.handleNewsPostCreatedEvent(testEvent);
        result.get(); // Wait for completion

        verify(emailClient).sendEmail(
                anyList(), anyList(), anyList(), anyString(),
                argThat(content ->
                        content.contains("https://test.example.com/news/1") &&
                                !content.contains("//news")
                ),
                eq(true),
                anyList()
        );
    }
}

