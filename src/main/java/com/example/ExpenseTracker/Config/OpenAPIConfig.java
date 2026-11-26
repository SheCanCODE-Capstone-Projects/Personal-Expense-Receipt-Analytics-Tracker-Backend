package com.example.ExpenseTracker.Config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@SecurityScheme(
        type = SecuritySchemeType.HTTP,
        name = "BearerAuth",
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenAPIConfig {
    @Bean
    public OpenAPI PersonalExpenseTrackerOpenAPI(){
        return  new OpenAPI().info(new Info()
                .title("Personal Expense Tracker API")
                .version("1.0")
                .description("Expense tracking API. Sign in with POST /api/auth/login (demo: amina@demo.local / Demo12345) "
                        + "or the OTP endpoints. The session is stored in HttpOnly cookies, so every other request "
                        + "on this page is signed in automatically; Authorize is only needed for a bearer token.")
                )
                // A relative server URL makes Swagger UI call the host it was loaded from, with the same
                // scheme. Behind an HTTPS proxy (Railway, Caddy) a generated http:// URL would be blocked.
                .servers(List.of(new Server().url("/").description("This server")))
                .security(List.of(new SecurityRequirement().addList("BearerAuth")));
    }
}

