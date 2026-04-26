package com.media_service.resource;

import com.media_service.entity.MediaFile;
import com.media_service.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
@Slf4j
public class MediaResource {

    private final MediaService mediaService;

    @PostMapping("/upload")
    public ResponseEntity<MediaFile> upload(
            @RequestParam("file")                                    MultipartFile file,
            @RequestHeader(value = "X-User-Id",    required = false) String uploaderIdHeader,
            @RequestParam(value  = "uploaderId",   required = false) String uploaderIdParam,
            @RequestParam("roomId")                                  String roomId,
            @RequestParam(value  = "messageId",    required = false) String messageId) throws IOException {

        // X-User-Id  → injected by gateway when request is proxied (standard path)
        // uploaderId → sent as a form field by Angular for the direct-upload path
        // No JWT parsing here — security lives only in auth-service / api-gateway.
        String uid = uploaderIdHeader != null ? uploaderIdHeader
                   : uploaderIdParam   != null ? uploaderIdParam
                   : "anonymous";

        log.info("[Media] Upload request from user {} for room {}", uid, roomId);

        if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
            return ResponseEntity.ok(mediaService.uploadImage(file, uid, roomId, messageId));
        }
        return ResponseEntity.ok(mediaService.uploadFile(file, uid, roomId, messageId));
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<MediaFile>> getByRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(mediaService.getFilesByRoom(roomId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<MediaFile>> getByUser(@PathVariable String userId) {
        return ResponseEntity.ok(mediaService.getFilesByUploader(userId));
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<MediaFile> getById(@PathVariable String mediaId) {
        return mediaService.getFileById(mediaId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(@PathVariable String mediaId) throws IOException {
        mediaService.deleteFile(mediaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count/room/{roomId}")
    public ResponseEntity<Long> getCount(@PathVariable String roomId) {
        return ResponseEntity.ok(mediaService.getFileCount(roomId));
    }

    // ── Local File Serving (Dev only) ────────────────────────────────

    @GetMapping("/view/{filename}")
    public ResponseEntity<Resource> view(@PathVariable String filename) throws IOException {
        Path path = Paths.get("./uploads").resolve(filename);
        Resource resource = new UrlResource(path.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    @GetMapping("/view/thumbs/{filename}")
    public ResponseEntity<Resource> viewThumb(@PathVariable String filename) throws IOException {
        Path path = Paths.get("./uploads/thumbs").resolve(filename);
        Resource resource = new UrlResource(path.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }
}
