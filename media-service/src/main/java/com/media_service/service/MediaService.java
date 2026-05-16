package com.media_service.service;

import com.media_service.entity.MediaFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface MediaService {
    
    MediaFile uploadFile(MultipartFile file, String uploaderId, String roomId, String messageId) throws IOException;
    
    MediaFile uploadImage(MultipartFile file, String uploaderId, String roomId, String messageId) throws IOException;
    
    Optional<MediaFile> getFileById(String mediaId);
    
    List<MediaFile> getFilesByRoom(String roomId);
    
    List<MediaFile> getFilesByUploader(String uploaderId);
    
    void deleteFile(String mediaId) throws IOException;
    
    String generateThumbnail(String originalUrl) throws IOException;
    
    List<MediaFile> getAllFiles();
    
    long getFileCount(String roomId);
}
