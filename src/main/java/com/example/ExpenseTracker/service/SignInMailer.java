package com.example.ExpenseTracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Sends sign-in emails through Brevo (https://app.brevo.com).
 * <ul>
 *   <li><b>Brevo HTTP API</b> when {@code BREVO_API_KEY} is set. It uses HTTPS (port 443), so it works on hosts
 *       that block outgoing SMTP, such as Railway's free and hobby plans.</li>
 *   <li><b>SMTP</b> otherwise, by default Brevo's relay {@code smtp-relay.brevo.com:587} with the SMTP login and
 *       SMTP key from Brevo → SMTP &amp; API. (Development compose points SMTP at Mailpit instead.)</li>
 * </ul>
 * Failures are thrown as {@link org.springframework.mail.MailException}, whichever transport is used.
 */
@Slf4j
@Component
public class SignInMailer {
    private final JavaMailSender smtp;
    private final RestClient brevo;
    private final String apiKey;
    private final String fromAddress;
    private final String fromName;

    public SignInMailer(JavaMailSender smtp,
                        @Value("${app.mail.brevo-api-key:}") String apiKey,
                        @Value("${app.mail.brevo-api-url:https://api.brevo.com/v3/smtp/email}") String apiUrl,
                        @Value("${app.auth.mail-from}") String fromAddress,
                        @Value("${app.mail.from-name:Expense Tracker}") String fromName) {
        this.smtp = smtp;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.brevo = RestClient.builder().baseUrl(apiUrl).build();
        log.info("Sign-in email is sent through {}", usesApi() ? "the Brevo HTTP API" : "SMTP");
    }

    public boolean usesApi() {
        return !apiKey.isEmpty();
    }

    public void send(String to, String subject, String text) {
        if (usesApi()) {
            sendWithApi(to, subject, text);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromName.isBlank() ? fromAddress : fromName + " <" + fromAddress + ">");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        smtp.send(message);
    }

    private void sendWithApi(String to, String subject, String text) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", fromName, "email", fromAddress),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", text);
        try {
            brevo.post()
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // Brevo explains the refusal in the body, e.g. an unverified sender or an invalid key.
            throw new MailSendException("Brevo API returned " + ex.getStatusCode().value() + ": "
                    + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new MailSendException("Brevo API unreachable: " + ex.getMessage(), ex);
        }
    }
}
