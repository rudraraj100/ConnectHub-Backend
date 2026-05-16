package com.media_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
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
 *  1. Set app.media.s3.enabled=true in application-prod.yaml
 *  2. Attach the ConnectHub-EC2-S3Role IAM Role to the EC2 instance
 *     (no AWS access keys needed — the SDK auto-discovers instance credentials)
 */
@Configuration
public class MediaConfig {

    @Value("${app.media.s3.enabled:false}")
    private boolean s3Enabled;

    @Value("${app.media.s3.region:eu-north-1}")
    private String awsRegion;

    @Bean
    public Optional<S3Client> s3Client() {
        if (s3Enabled) {
            // Uses EC2 IAM Role credentials automatically via DefaultCredentialsProvider.
            // No access keys needed — the SDK discovers them from the instance metadata.
            S3Client client = S3Client.builder()
                    .region(Region.of(awsRegion))
                    .build();
            return Optional.of(client);
        }
        // S3 disabled for local dev — MediaServiceImpl falls back to ./uploads on disk.
        return Optional.empty();
    }
}
