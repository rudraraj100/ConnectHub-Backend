package com.media_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenApiConfig — Media Service Swagger / OpenAPI 3.0 configuration.
 *
 * Exposes a machine-readable spec at:  GET /api-docs
 * Exposes the interactive UI at:       GET /swagger-ui.html
 *
 * The API Gateway aggregates this spec at:
 *   http://localhost:8080/swagger-ui.html  →  dropdown: "Media Service"
 *
 * The /media/view/** endpoints are intentionally public (no JWT required)
 * because media URLs are embedded in chat messages and must be loadable
 * directly by <img> and <video> tags in the browser.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mediaServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectHub — Media Service")
                        .version("1.0.0")
                        .description("""
                                Handles file upload, storage, retrieval and streaming for ConnectHub.
                                
                                **Responsibilities:**
                                - Upload images (auto-generates JPEG thumbnail in `uploads/thumbs/`)
                                - Upload arbitrary files (video, documents)
                                - Serve files with full HTTP Range request support (206 Partial Content)
                                  for smooth browser video playback
                                - Metadata reads: files by room, by uploader, by ID
                                - Delete files (removes from disk and DB)
                                
                                **File storage:** Local disk at `${app.media.local-path}` (configurable).
                                Planned: S3 backend via `${app.media.s3.bucket}`.
                                
                                **Limits:** Max file size `50 MB` (configurable via `spring.servlet.multipart`).
                                
                                **Security notes:**
                                - `POST /media/upload` — requires JWT (authenticated users only)
                                - `GET /media/view/**` — **public** (no JWT); URLs are embedded in messages
                                - Path traversal is blocked server-side for all file reads
                                """)
                        .contact(new Contact()
                                .name("ConnectHub Engineering")
                                .email("engineering@connecthub.io"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://connecthub.io/terms")))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("API Gateway (recommended for testing)"))
                .addServersItem(new Server()
                        .url("http://localhost:8084")
                        .description("Media Service (direct — dev only)"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Obtain a JWT from POST /auth/login and paste the token here.")));
    }
}
