package com.sportevents.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sportEventsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sport Events API")
                        .description("""
                                REST API for managing sport events.

                                ## Authentication
                                Write operations (POST, PATCH) require the `X-API-Key` header.
                                Set it to `dev-api-key-change-in-production` for local development.

                                ## Real-time updates (SSE)
                                Subscribe to live status changes via Server-Sent Events:
                                - `GET /api/events/subscribe` — all events
                                - `GET /api/events/{id}/subscribe` — one specific event

                                SSE event names pushed by the server:
                                - `event-created` — a new event was created
                                - `event-update`  — an event's status changed

                                ## Status transition rules
                                | From | To | Allowed |
                                |---|---|---|
                                | INACTIVE | ACTIVE | ✅ (startTime must be future) |
                                | ACTIVE | FINISHED | ✅ |
                                | INACTIVE | FINISHED | ❌ |
                                | ACTIVE | INACTIVE | ❌ |
                                | FINISHED | any | ❌ |
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Sport Events Team").email("api@sportevents.com"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development")))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-API-Key")
                                        .description("API key required for write operations")));
    }
}
