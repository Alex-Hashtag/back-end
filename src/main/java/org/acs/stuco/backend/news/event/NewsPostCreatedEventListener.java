package org.acs.stuco.backend.news.event;

import org.acs.stuco.backend.email.EmailClient;
import org.acs.stuco.backend.news.NewsPost;
import org.acs.stuco.backend.user.User;
import org.acs.stuco.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.CompletableFuture;


@Component
public class NewsPostCreatedEventListener
{
    private static final Logger logger = LoggerFactory.getLogger(NewsPostCreatedEventListener.class);

    private final EmailClient emailClient;
    private final UserRepository userRepository;

    public NewsPostCreatedEventListener(EmailClient emailClient, UserRepository userRepository)
    {
        this.emailClient = emailClient;
        this.userRepository = userRepository;
    }

    @Async
    @TransactionalEventListener
    public CompletableFuture<Void> handleNewsPostCreatedEvent(NewsPostCreatedEvent event)
    {
        NewsPost newsPost = event.newsPost();

        // Get all verified users using the new repository method
        List<User> verifiedUsers = userRepository.findByEmailVerified(true);
        if (verifiedUsers.isEmpty())
        {
            logger.info("No verified users to notify about new news post");
            return CompletableFuture.completedFuture(null);
        }

        // Prepare email content
        String contentPreview = newsPost.getContent().length() > 100
                ? newsPost.getContent().substring(0, 100) + "..."
                : newsPost.getContent();

        String readMoreLink = "http://localhost:5173/news/" + newsPost.getId();

        String htmlContent = String.format(
                "<html>" +
                        "<body>" +
                        "<h2>%s</h2>" +
                        "<p>%s</p>" +
                        "<p><a href='%s'>Read more...</a></p>" +
                        "%s" +
                        "</body>" +
                        "</html>",
                newsPost.getTitle(),
                contentPreview,
                readMoreLink,
                newsPost.getBannerPhotoUrl() != null
                        ? String.format("<img src='%s' alt='News banner' style='max-width: 600px;'/>", newsPost.getBannerPhotoUrl())
                        : ""
        );

        // Send emails to all verified users
        try
        {
            List<String> emails = verifiedUsers.stream()
                    .map(User::getEmail)
                    .toList();

            logger.info("Sending news notification to {} verified users", emails.size());

            emailClient.sendEmail(
                    emails,
                    List.of(),
                    List.of(),
                    "New STUCO News: " + newsPost.getTitle(),
                    htmlContent,
                    true,  // HTML content
                    List.of()
            );

            return CompletableFuture.completedFuture(null);
        } catch (Exception e)
        {
            logger.error("Failed to send news notification emails", e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
