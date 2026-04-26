package com.media_service.service;

import com.media_service.entity.MediaFile;
import com.media_service.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;
    // Optional S3Client — will be null if not configured in a Bean
    private final Optional<S3Client> s3Client;

    @Value("${app.media.s3.bucket:connecthub-media}")
    private String bucketName;

    @Value("${app.media.local-path:./uploads}")
    private String localPath;

    /** Gateway base URL — prefixed on all local file URLs so Angular can load them via the gateway */
    @Value("${app.media.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public MediaFile uploadFile(MultipartFile file, String uploaderId, String roomId, String messageId) throws IOException {
        String mediaId = UUID.randomUUID().toString();
        String extension = getExtension(file.getOriginalFilename());
        String fileName = mediaId + extension;
        
        String url;
        if (s3Client.isPresent()) {
            url = uploadToS3(file, fileName);
        } else {
            url = uploadToLocal(file, fileName);
        }

        MediaFile mediaFile = MediaFile.builder()
                .mediaId(mediaId)
                .uploaderId(uploaderId)
                .roomId(roomId)
                .messageId(messageId)
                .filename(fileName)
                .originalName(file.getOriginalFilename())
                .url(url)
                .mimeType(file.getContentType())
                .sizeKb(file.getSize() / 1024)
                .build();

        return mediaRepository.save(mediaFile);
    }

    @Override
    public MediaFile uploadImage(MultipartFile file, String uploaderId, String roomId, String messageId) throws IOException {
        // Read bytes ONCE upfront — InputStream can only be read once.
        // uploadFile() streams it to disk; if we called it first the stream would be consumed.
        byte[] fileBytes = file.getBytes();

        // Save the file using the buffered bytes
        String mediaId   = UUID.randomUUID().toString();
        String extension = getExtension(file.getOriginalFilename());
        String fileName  = mediaId + extension;

        String url = s3Client.isPresent()
                ? uploadBytesToS3(fileBytes, fileName, file.getContentType())
                : uploadBytesToLocal(fileBytes, fileName);

        MediaFile mediaFile = MediaFile.builder()
                .mediaId(mediaId)
                .uploaderId(uploaderId)
                .roomId(roomId)
                .messageId(messageId)
                .filename(fileName)
                .originalName(file.getOriginalFilename())
                .url(url)
                .mimeType(file.getContentType())
                .sizeKb((long) fileBytes.length / 1024)
                .build();

        // Generate thumbnail and extract dimensions from the same bytes
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(fileBytes));
            if (img != null) {
                mediaFile.setWidth(img.getWidth());
                mediaFile.setHeight(img.getHeight());

                // Thumbnail
                BufferedImage thumb = Scalr.resize(img, Scalr.Method.AUTOMATIC, Scalr.Mode.FIT_TO_WIDTH, 300);
                if (s3Client.isPresent()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(thumb, "jpg", baos);
                    uploadBytesToS3(baos.toByteArray(), "thumbs/" + fileName, "image/jpeg");
                    mediaFile.setThumbnailUrl("s3://" + bucketName + "/thumbs/" + fileName);
                } else {
                    Path thumbDir = Paths.get(localPath, "thumbs");
                    if (!Files.exists(thumbDir)) Files.createDirectories(thumbDir);
                    // Always use .jpg for the thumbnail — ImageIO.write encodes JPEG bytes
                    // regardless of the original extension, so saving as .png would produce
                    // a corrupt file that browsers refuse to render.
                    String thumbFileName = mediaId + ".jpg";
                    ImageIO.write(thumb, "jpg", thumbDir.resolve(thumbFileName).toFile());
                    mediaFile.setThumbnailUrl(baseUrl + "/media/view/thumbs/" + thumbFileName);
                }
            }
        } catch (Exception e) {
            log.error("[Media] Thumbnail generation failed for {}: {}", fileName, e.getMessage());
            // Non-fatal — save without thumbnail
        }

        return mediaRepository.save(mediaFile);
    }

    // ── Byte-array based upload helpers (avoids InputStream re-read) ──

    private String uploadBytesToLocal(byte[] bytes, String fileName) throws IOException {
        Path root = Paths.get(localPath);
        if (!Files.exists(root)) Files.createDirectories(root);
        Files.write(root.resolve(fileName), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        // Return an absolute URL through the gateway so Angular can load it without CORS/routing issues
        return baseUrl + "/media/view/" + fileName;
    }

    private String uploadBytesToS3(byte[] bytes, String key, String contentType) {
        s3Client.get().putObject(
                PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
    }


    @Override
    public Optional<MediaFile> getFileById(String mediaId) {
        return mediaRepository.findById(mediaId);
    }

    @Override
    public List<MediaFile> getFilesByRoom(String roomId) {
        return mediaRepository.findByRoomId(roomId);
    }

    @Override
    public List<MediaFile> getFilesByUploader(String uploaderId) {
        return mediaRepository.findByUploaderId(uploaderId);
    }

    @Override
    public void deleteFile(String mediaId) throws IOException {
        mediaRepository.findById(mediaId).ifPresent(m -> {
            // Delete from storage
            if (s3Client.isPresent()) {
                // S3 delete logic
            } else {
                try {
                    Files.deleteIfExists(Paths.get(localPath, m.getFilename()));
                    if (m.getThumbnailUrl() != null) {
                        Files.deleteIfExists(Paths.get(localPath, "thumbs", m.getFilename()));
                    }
                } catch (IOException e) {
                    log.error("Failed to delete local file: {}", e.getMessage());
                }
            }
            mediaRepository.delete(m);
        });
    }

    @Override
    public String generateThumbnail(String originalUrl) {
        // Implementation for existing files if needed
        return null;
    }

    private String generateThumbnailFromMultipart(MultipartFile file, String fileName) throws IOException {
        BufferedImage src = ImageIO.read(file.getInputStream());
        if (src == null) return null;
        
        BufferedImage thumb = Scalr.resize(src, Scalr.Method.AUTOMATIC, Scalr.Mode.FIT_TO_WIDTH, 300);
        
        if (s3Client.isPresent()) {
            // Upload thumb to S3 /thumbs/ folder
            return "s3://bucket/thumbs/" + fileName;
        } else {
            Path thumbDir = Paths.get(localPath, "thumbs");
            if (!Files.exists(thumbDir)) Files.createDirectories(thumbDir);
            
            File thumbFile = thumbDir.resolve(fileName).toFile();
            ImageIO.write(thumb, "jpg", thumbFile);
            return "/media/view/thumbs/" + fileName;
        }
    }

    @Override
    public List<MediaFile> getAllFiles() {
        return mediaRepository.findAll();
    }

    @Override
    public long getFileCount(String roomId) {
        // countByRoomId returns boxed Long — coalesce null to 0 for empty tables
        Long count = mediaRepository.countByRoomId(roomId);
        return count != null ? count : 0L;
    }

    // ── Storage Helpers ──────────────────────────────────────────────

    private String uploadToS3(MultipartFile file, String fileName) throws IOException {
        s3Client.get().putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(file.getContentType())
                .build(), RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, fileName);
    }

    private String uploadToLocal(MultipartFile file, String fileName) throws IOException {
        Path root = Paths.get(localPath);
        if (!Files.exists(root)) Files.createDirectories(root);
        Files.copy(file.getInputStream(), root.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        // Return an absolute URL through the gateway so Angular can load it without CORS/routing issues
        return baseUrl + "/media/view/" + fileName;
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf(".");
        return dot > 0 ? fileName.substring(dot) : "";
    }
}
