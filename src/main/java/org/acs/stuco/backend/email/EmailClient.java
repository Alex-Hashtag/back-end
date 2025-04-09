package org.acs.stuco.backend.email;

import org.acs.stuco.backend.exceptions.EmailSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class EmailClient {

    private static final Logger logger = LoggerFactory.getLogger(EmailClient.class);

    @Value("${acs.service.mail.url}")
    private String emailServiceUrl;

    @Autowired
    private RestTemplate restTemplate;

    public void sendEmail(List<String> to, List<String> cc, List<String> bcc,
                          String subject, String body, boolean html,
                          List<Attachment> attachments) {
        try {
            AdvancedEmailRequest request = new AdvancedEmailRequest(to, cc, bcc, subject, body, html, attachments);
            logger.debug("Sending email to: {}", to);

            ResponseEntity<Void> response = restTemplate.postForEntity(
                    emailServiceUrl, // use the injected instance field here
                    request,
                    Void.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("Email service returned {} for {}", response.getStatusCode(), to);
            }
        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new EmailSendException("Failed to send email", e);
        }
    }
}
