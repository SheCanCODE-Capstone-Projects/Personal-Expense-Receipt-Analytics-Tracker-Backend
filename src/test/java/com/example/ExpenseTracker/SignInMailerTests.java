package com.example.ExpenseTracker;

import com.example.ExpenseTracker.service.SignInMailer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Brevo delivery: the HTTP API when BREVO_API_KEY is set, SMTP otherwise. Uses a local fake of the Brevo API. */
class SignInMailerTests {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String fakeBrevo(int status, AtomicReference<String> apiKey, AtomicReference<String> body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = (status == 201 ? "{\"messageId\":\"<1@brevo>\"}" : "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email";
    }

    @Test
    void sendsThroughTheBrevoApiWhenAKeyIsSet() throws Exception {
        var key = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        JavaMailSender smtp = mock(JavaMailSender.class);
        var mailer = new SignInMailer(smtp, "xkeysib-test", fakeBrevo(201, key, body), "codes@tracker.rw", "Expense Tracker");

        mailer.send("amina@demo.local", "Your code", "Your code is 123456.");

        assertTrue(mailer.usesApi());
        assertEquals("xkeysib-test", key.get());
        assertTrue(body.get().contains("\"email\":\"codes@tracker.rw\""), body.get());
        assertTrue(body.get().contains("\"to\":[{\"email\":\"amina@demo.local\"}]"), body.get());
        assertTrue(body.get().contains("Your code is 123456."), body.get());
        verifyNoInteractions(smtp);
    }

    @Test
    void brevoRefusalBecomesAMailExceptionWithTheReason() throws Exception {
        var mailer = new SignInMailer(mock(JavaMailSender.class), "xkeysib-wrong",
                fakeBrevo(401, new AtomicReference<>(), new AtomicReference<>()), "codes@tracker.rw", "Expense Tracker");
        var error = assertThrows(MailSendException.class, () -> mailer.send("a@b.rw", "s", "t"));
        assertTrue(error.getMessage().contains("401") && error.getMessage().contains("Key not found"), error.getMessage());
    }

    @Test
    void usesSmtpWhenNoApiKeyIsSet() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        var mailer = new SignInMailer(smtp, "", "http://unused", "codes@tracker.rw", "Expense Tracker");
        mailer.send("a@b.rw", "Subject", "Text");
        verify(smtp).send(any(SimpleMailMessage.class));
        assertFalse(mailer.usesApi());
    }
}
