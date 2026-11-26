package com.example.ExpenseTracker.Config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
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
                .description("Expense tracking API. For passwordless sign-in, start with the Authentication endpoints.")
                ).security(List.of(new SecurityRequirement().addList("BearerAuth")))
                ;
    }
}

