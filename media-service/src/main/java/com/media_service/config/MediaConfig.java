package com.media_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Optional;

/**
 * MediaConfig — provides infrastructure beans for the media-service.
 *
 * Why Optional<S3Client> as a Bean?
 * MediaServiceImpl uses @RequiredArgsConstructor (Lombok), which generates a
 * constructor that takes Optional<S3Client> as a parameter. Spring treats this as
 * a required dependency and looks for a registered bean of that exact type.
 * Without this bean, Spring fails to create MediaServiceImpl at startup
 * (NoSuchBeanDefinitionException), and every upload request returns a 500.
 *
 * To enable real S3 uploads:
 *  1. Set app.media.s3.enabled=true in application.yaml
 *  2. Configure AWS credentials (env vars or ~/.aws/credentials)
 *  3. Build S3Client with your region and return Optional.of(client)
 */
@Configuration
public class MediaConfig {

    @Bean
    public Optional<S3Client> s3Client() {
        // S3 is disabled for local dev — MediaServiceImpl will fall back to
        // ./uploads on disk. Change this to Optional.of(S3Client.builder()...)
        // when you're ready to enable cloud storage.
        return Optional.empty();
    }
}
