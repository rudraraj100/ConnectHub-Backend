package com.media_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "media_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaFile {

    @Id
    @Column(length = 36)
    private String mediaId; // UUID

    @Column(length = 36)
    private String uploaderId;  // UUID string injected by API Gateway
    
    @Column(length = 100)   // DM room IDs: "dm_<uuid36>_<uuid36>" = 76 chars
    private String roomId;
    
    @Column(length = 100)   // future-proof: allow composite message IDs
    private String messageId;

    private String filename;
    private String originalName;
    private String url; // S3 URL
    private String thumbnailUrl;
    private String mimeType;
    private Long sizeKb;
    private Integer width;
    private Integer height;

    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
