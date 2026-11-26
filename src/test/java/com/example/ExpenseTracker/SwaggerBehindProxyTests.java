package com.example.ExpenseTracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The deployed API sits behind an HTTPS proxy (Railway, Caddy) that talks plain HTTP to the app. */
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerBehindProxyTests {
    private static final String PUBLIC = "https://api.example.org";

    @Autowired MockMvc mvc;
    @MockitoBean JavaMailSender mail;

    @Test
    void apiDocsUseARelativeServerUrlSoSwaggerKeepsHttps() throws Exception {
        mvc.perform(get("/v3/api-docs").header("X-Forwarded-Proto", "https").header("Host", "api.example.org"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void swaggerCanPostToItsOwnOriginBehindTheProxy() throws Exception {
        // Not in CORS_ALLOWED_ORIGINS, but it is the API's own public origin, so it is same-origin.
        mvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-Proto", "https").header("Host", "api.example.org")
                        .header("Origin", PUBLIC)
                        .contentType("application/json")
                        .content("{\"email\":\"nobody@example.org\",\"password\":\"Wrong12345\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otherOriginsAreStillRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-Proto", "https").header("Host", "api.example.org")
                        .header("Origin", "https://evil.example.net")
                        .contentType("application/json")
                        .content("{\"email\":\"nobody@example.org\",\"password\":\"Wrong12345\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rootOpensSwagger() throws Exception {
        mvc.perform(get("/")).andExpect(status().isFound()).andExpect(header().string("Location", "swagger-ui/index.html"));
    }
}
