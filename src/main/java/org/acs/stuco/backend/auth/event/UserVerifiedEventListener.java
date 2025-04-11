package org.acs.stuco.backend.auth.event;

import org.acs.stuco.backend.email.EmailClient;
import org.acs.stuco.backend.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.CompletableFuture;


@Component
public class UserVerifiedEventListener
{
    private static final Logger logger = LoggerFactory.getLogger(UserVerifiedEventListener.class);

    private final EmailClient emailClient;

    public UserVerifiedEventListener(EmailClient emailClient)
    {
        this.emailClient = emailClient;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public CompletableFuture<Void> handleUserVerifiedEvent(UserVerifiedEvent event)
    {
        User user = event.user();
        try
        {
            String welcomeMessage = String.format(
                    "Welcome to STUCO, %s!\\n\\n" +
                            "Your email %s has been successfully verified.\\n" +
                            "You can now log in to your account and start using all the features.\\n\\n" +
                            "Best regards,\\n" +
                            "The STUCO Team",
                    user.getName(),
                    user.getEmail()
            );

            emailClient.sendEmail(
                    List.of(user.getEmail()),
                    List.of(),
                    List.of(),
                    "Welcome to STUCO!",
                    welcomeMessage,
                    false,
                    List.of()
            );
            return CompletableFuture.completedFuture(null);
        } catch (Exception e)
        {
            logger.error("Failed to send welcome email to {}", user.getEmail(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
}


