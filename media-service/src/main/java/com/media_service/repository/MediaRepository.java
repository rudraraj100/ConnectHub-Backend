package com.media_service.repository;

import com.media_service.entity.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<MediaFile, String> {
    
    List<MediaFile> findByUploaderId(String uploaderId);
    
    List<MediaFile> findByRoomId(String roomId);
    
    Optional<MediaFile> findByMessageId(String messageId);
    
    List<MediaFile> findByMimeTypeStartingWith(String mimeType);
    
    Long countByRoomId(String roomId);
    
    void deleteByMediaId(String mediaId);
}
