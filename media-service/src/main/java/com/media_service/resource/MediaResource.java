package com.media_service.resource;

import com.media_service.entity.MediaFile;
import com.media_service.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.List;

/**
 * MediaResource provides endpoints for uploading and viewing media files (images/videos).
 * It supports partial content (Range requests) for smooth video playback.
 */
@Slf4j
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "File upload, retrieval, streaming (Range requests) and thumbnail serving")
public class MediaResource {

    private final MediaService mediaService;

    /** Same property that MediaServiceImpl uses — guarantees identical resolution. */
    @Value("${app.media.local-path:./uploads}")
    private String localPath;

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Uploads a file. Automatically distinguishes between images (thumbnails generated)
     * and other file types (documents, videos).
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Upload a media file (image or video)",
            description = """
                    Accepts a multipart file upload and persists it to local disk.
                    
                    - **Images** (`image/*`): a JPEG thumbnail is auto-generated under `uploads/thumbs/`
                    - **Videos / other**: stored as-is; served via Range requests for smooth playback
                    
                    The `uploaderId` is read from the `X-User-Id` header (injected by the gateway).
                    A `roomId` is required to associate the file with a chat room.
                    Optionally pass `messageId` to link the media to a specific message.
                    """)
    @ApiResponse(responseCode = "200", description = "File uploaded successfully",
            content = @Content(schema = @Schema(implementation = MediaFile.class)))
    @ApiResponse(responseCode = "400", description = "Missing required parameters", content = @Content)
    @ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @ApiResponse(responseCode = "413", description = "File exceeds 50 MB limit", content = @Content)
    @ApiResponse(responseCode = "500", description = "IO error during storage", content = @Content)
    public ResponseEntity<MediaFile> upload(
            @Parameter(description = "The file to upload (max 50 MB)", required = true)
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Uploader's userId (from X-User-Id gateway header)", hidden = true)
            @RequestHeader(value = "X-User-Id", required = false) String uploaderIdHeader,

            @Parameter(description = "Fallback uploaderId (for legacy / internal callers)", hidden = true)
            @RequestParam(value = "uploaderId", required = false) String uploaderIdParam,

            @Parameter(description = "ID of the chat room this media belongs to", required = true)
            @RequestParam("roomId") String roomId,

            @Parameter(description = "ID of the message to associate with (optional)")
            @RequestParam(value = "messageId", required = false) String messageId) throws IOException {

        String uid;
        if (uploaderIdHeader != null) {
            uid = uploaderIdHeader;
        } else if (uploaderIdParam != null) {
            uid = uploaderIdParam;
        } else {
            uid = "anonymous";
        }

        log.info("[Media] Upload request from user {} for room {}", uid, roomId);

        if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
            return ResponseEntity.ok(mediaService.uploadImage(file, uid, roomId, messageId));
        }
        return ResponseEntity.ok(mediaService.uploadFile(file, uid, roomId, messageId));
    }

    // ── Metadata reads ────────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List all media files in a room",
               description = "Returns metadata for every file uploaded to the specified room.")
    @ApiResponse(responseCode = "200", description = "File list returned")
    @ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    public ResponseEntity<List<MediaFile>> getByRoom(
            @Parameter(description = "Room ID", required = true)
            @PathVariable String roomId) {
        return ResponseEntity.ok(mediaService.getFilesByRoom(roomId));
    }

    @GetMapping("/user/{userId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List all media files uploaded by a user",
               description = "Returns metadata for every file uploaded by the given userId.")
    @ApiResponse(responseCode = "200", description = "File list returned")
    @ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    public ResponseEntity<List<MediaFile>> getByUser(
            @Parameter(description = "Uploader's userId", required = true)
            @PathVariable String userId) {
        return ResponseEntity.ok(mediaService.getFilesByUploader(userId));
    }

    @GetMapping("/{mediaId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get media file metadata by ID")
    @ApiResponse(responseCode = "200", description = "Media file metadata",
            content = @Content(schema = @Schema(implementation = MediaFile.class)))
    @ApiResponse(responseCode = "404", description = "Media file not found", content = @Content)
    public ResponseEntity<MediaFile> getById(
            @Parameter(description = "Media file ID (UUID)", required = true)
            @PathVariable String mediaId) {
        return mediaService.getFileById(mediaId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{mediaId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a media file",
               description = "Removes the file from disk and deletes its database record. Irreversible.")
    @ApiResponse(responseCode = "204", description = "File deleted")
    @ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    @ApiResponse(responseCode = "404", description = "Media file not found", content = @Content)
    public ResponseEntity<Void> delete(
            @Parameter(description = "Media file ID (UUID)", required = true)
            @PathVariable String mediaId) throws IOException {
        mediaService.deleteFile(mediaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count/room/{roomId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get total media file count for a room")
    @ApiResponse(responseCode = "200", description = "File count returned")
    @ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    public ResponseEntity<Long> getCount(
            @Parameter(description = "Room ID", required = true)
            @PathVariable String roomId) {
        return ResponseEntity.ok(mediaService.getFileCount(roomId));
    }

    // ── File serving ──────────────────────────────────────────────────────────

    /**
     * Serves a media file (image or video) from the configured upload directory.
     * Supports HTTP Range requests (206 Partial Content) for smooth video streaming.
     */
    @GetMapping("/view/{filename}")
    @Operation(
            summary = "Stream or download a media file (public — no JWT required)",
            description = """
                    Serves files from `${app.media.local-path}` with full HTTP Range request support.
                    
                    **Range requests (video streaming):**
                    - Send `Range: bytes=N-M` → `206 Partial Content` with the requested byte range
                    - No `Range` header → `200 OK` with the complete file
                    - `Accept-Ranges: bytes` is always included so browsers can seek
                    
                    **Security:** This endpoint is intentionally **public** (no JWT required).
                    Media URLs are embedded in chat messages and must be directly loadable by
                    browser `<img>` and `<video>` tags without an Authorization header.
                    Path traversal attacks are blocked server-side.
                    """)
    @ApiResponse(responseCode = "200", description = "Full file content")
    @ApiResponse(responseCode = "206", description = "Partial content (Range request fulfilled)")
    @ApiResponse(responseCode = "400", description = "Path traversal attempt blocked")
    @ApiResponse(responseCode = "404", description = "File not found or not readable")
    public ResponseEntity<byte[]> view(
            @Parameter(description = "Filename as stored on disk (UUID-prefixed)", required = true)
            @PathVariable String filename,
            @RequestHeader HttpHeaders requestHeaders) throws IOException {

        Path uploadsRoot = Paths.get(localPath).toAbsolutePath().normalize();
        Path filePath    = uploadsRoot.resolve(filename).normalize();

        // Path traversal guard
        if (!filePath.startsWith(uploadsRoot)) {
            log.warn("[Media] Path traversal attempt blocked: {}", filename);
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            log.warn("[Media] File not found: {} (resolved: {})", filename, filePath);
            return ResponseEntity.notFound().build();
        }

        String detected = Files.probeContentType(filePath);
        MediaType mediaType = detected != null
                ? MediaType.parseMediaType(detected)
                : MediaType.APPLICATION_OCTET_STREAM;

        long fileSize = Files.size(filePath);
        List<HttpRange> ranges = requestHeaders.getRange();

        if (!ranges.isEmpty()) {
            // ── 206 Partial Content ───────────────────────────────────────────
            HttpRange range = ranges.get(0);
            long start  = range.getRangeStart(fileSize);
            long end    = range.getRangeEnd(fileSize);
            long length = end - start + 1;

            byte[] chunk = new byte[(int) length];
            try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
                raf.seek(start);
                raf.readFully(chunk);
            }

            log.debug("[Media] Range {}-{}/{} for {}", start, end, fileSize, filename);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(length)
                    .body(chunk);

        } else {
            // ── 200 OK — full file ────────────────────────────────────────────
            byte[] bytes = Files.readAllBytes(filePath);

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(bytes.length)
                    .body(bytes);
        }
    }

    /**
     * Serves a thumbnail from the configured upload directory's thumbs/ subdirectory.
     * Thumbnails are small JPEGs — no range support needed.
     */
    @GetMapping("/view/thumbs/{filename}")
    @Operation(
            summary = "Serve an image thumbnail (public — no JWT required)",
            description = """
                    Returns the JPEG thumbnail generated during image upload.
                    Thumbnails are stored under `${app.media.local-path}/thumbs/`.
                    This endpoint is **public** for the same reason as `/view/{filename}`.
                    """)
    @ApiResponse(responseCode = "200", description = "Thumbnail image bytes")
    @ApiResponse(responseCode = "400", description = "Path traversal attempt blocked")
    @ApiResponse(responseCode = "404", description = "Thumbnail not found")
    public ResponseEntity<byte[]> viewThumb(
            @Parameter(description = "Thumbnail filename (UUID-prefixed .jpg)", required = true)
            @PathVariable String filename) throws IOException {

        Path thumbRoot = Paths.get(localPath, "thumbs").toAbsolutePath().normalize();
        Path filePath  = thumbRoot.resolve(filename).normalize();

        if (!filePath.startsWith(thumbRoot)) {
            log.warn("[Media] Path traversal attempt on thumbs: {}", filename);
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            return ResponseEntity.notFound().build();
        }

        String detected = Files.probeContentType(filePath);
        MediaType mediaType = detected != null
                ? MediaType.parseMediaType(detected)
                : MediaType.IMAGE_JPEG;

        byte[] bytes = Files.readAllBytes(filePath);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(bytes.length)
                .body(bytes);
    }
}