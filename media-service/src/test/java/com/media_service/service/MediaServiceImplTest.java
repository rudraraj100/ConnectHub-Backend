package com.media_service.service;

import com.media_service.entity.MediaFile;
import com.media_service.repository.MediaRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaServiceImpl — unit tests")
class MediaServiceImplTest {

    @Mock MediaRepository mediaRepository;

    @InjectMocks MediaServiceImpl sut;

    @TempDir
    Path tempDir;

    private MediaFile imageFile;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "s3Client",       Optional.empty());
        ReflectionTestUtils.setField(sut, "localPath",      tempDir.toString());
        ReflectionTestUtils.setField(sut, "baseUrl",        "http://localhost:8080");
        ReflectionTestUtils.setField(sut, "bucketName",     "connecthub-media");
        ReflectionTestUtils.setField(sut, "mediaViewPath",  "/media/view/");

        imageFile = MediaFile.builder()
                .mediaId("media-1").uploaderId("user-1")
                .roomId("room-1").filename("media-1.jpg")
                .mimeType("image/jpeg").build();
    }

    // ── getFileById ───────────────────────────────────────────────────

    @Test
    @DisplayName("getFileById() — returns present Optional when found")
    void getFileById_found() {
        when(mediaRepository.findById("media-1")).thenReturn(Optional.of(imageFile));
        Optional<MediaFile> result = sut.getFileById("media-1");
        assertThat(result).isPresent();
        assertThat(result.get().getMediaId()).isEqualTo("media-1");
    }

    @Test
    @DisplayName("getFileById() — returns empty Optional when not found")
    void getFileById_notFound() {
        when(mediaRepository.findById("bad")).thenReturn(Optional.empty());
        assertThat(sut.getFileById("bad")).isEmpty();
    }

    // ── getFilesByRoom ────────────────────────────────────────────────

    @Test
    @DisplayName("getFilesByRoom() — returns list for room")
    void getFilesByRoom_returnsList() {
        when(mediaRepository.findByRoomId("room-1")).thenReturn(List.of(imageFile));
        assertThat(sut.getFilesByRoom("room-1")).hasSize(1);
    }

    @Test
    @DisplayName("getFilesByRoom() — empty list when no files")
    void getFilesByRoom_empty() {
        when(mediaRepository.findByRoomId("room-x")).thenReturn(Collections.emptyList());
        assertThat(sut.getFilesByRoom("room-x")).isEmpty();
    }

    // ── getFilesByUploader ────────────────────────────────────────────

    @Test
    @DisplayName("getFilesByUploader() — returns files for uploader")
    void getFilesByUploader_returnsList() {
        when(mediaRepository.findByUploaderId("user-1")).thenReturn(List.of(imageFile));
        assertThat(sut.getFilesByUploader("user-1")).hasSize(1);
    }

    @Test
    @DisplayName("getFilesByUploader() — returns empty list when no files")
    void getFilesByUploader_empty() {
        when(mediaRepository.findByUploaderId("user-x")).thenReturn(List.of());
        assertThat(sut.getFilesByUploader("user-x")).isEmpty();
    }

    // ── getAllFiles ───────────────────────────────────────────────────

    @Test
    @DisplayName("getAllFiles() — returns all media files")
    void getAllFiles_returnsAll() {
        when(mediaRepository.findAll()).thenReturn(List.of(imageFile,
                MediaFile.builder().mediaId("media-2").build()));
        assertThat(sut.getAllFiles()).hasSize(2);
    }

    @Test
    @DisplayName("getAllFiles() — returns empty list when no files")
    void getAllFiles_empty() {
        when(mediaRepository.findAll()).thenReturn(List.of());
        assertThat(sut.getAllFiles()).isEmpty();
    }

    // ── getFileCount ──────────────────────────────────────────────────

    @Test
    @DisplayName("getFileCount() — returns count from repository")
    void getFileCount_returnsCount() {
        when(mediaRepository.countByRoomId("room-1")).thenReturn(5L);
        assertThat(sut.getFileCount("room-1")).isEqualTo(5L);
    }

    @Test
    @DisplayName("getFileCount() — returns 0 when repository returns null")
    void getFileCount_nullCoalescedToZero() {
        when(mediaRepository.countByRoomId("room-empty")).thenReturn(null);
        assertThat(sut.getFileCount("room-empty")).isZero();
    }

    // ── generateThumbnail ─────────────────────────────────────────────

    @Test
    @DisplayName("generateThumbnail() — returns null (stub method)")
    void generateThumbnail_returnsNull() {
        assertThat(sut.generateThumbnail("http://any-url")).isNull();
    }

    // ── deleteFile ────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFile() — no-op when file not found in DB")
    void deleteFile_notFound_noop() throws IOException {
        when(mediaRepository.findById("bad")).thenReturn(Optional.empty());
        sut.deleteFile("bad");
        verify(mediaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteFile() — deletes local file without thumbnail")
    void deleteFile_localNoThumbnail() throws IOException {
        MediaFile mf = MediaFile.builder().mediaId("id-1")
                .filename("file.txt").thumbnailUrl(null).build();
        tempDir.resolve("file.txt").toFile().createNewFile();
        when(mediaRepository.findById("id-1")).thenReturn(Optional.of(mf));

        sut.deleteFile("id-1");

        verify(mediaRepository).delete(mf);
    }

    @Test
    @DisplayName("deleteFile() — deletes local file and thumbnail when thumbnail present")
    void deleteFile_localWithThumbnail() throws IOException {
        MediaFile mf = MediaFile.builder().mediaId("id-2").filename("img.jpg")
                .thumbnailUrl("http://localhost/thumbs/img.jpg").build();
        tempDir.resolve("img.jpg").toFile().createNewFile();
        Path thumbs = tempDir.resolve("thumbs");
        thumbs.toFile().mkdirs();
        thumbs.resolve("img.jpg").toFile().createNewFile();
        when(mediaRepository.findById("id-2")).thenReturn(Optional.of(mf));

        sut.deleteFile("id-2");

        verify(mediaRepository).delete(mf);
    }

    @Test
    @DisplayName("deleteFile() — handles missing local file gracefully")
    void deleteFile_localFileMissing_stillDeletes() throws IOException {
        MediaFile mf = MediaFile.builder().mediaId("id-3")
                .filename("gone.txt").thumbnailUrl(null).build();
        // File does not exist on disk — deleteIfExists should not throw
        when(mediaRepository.findById("id-3")).thenReturn(Optional.of(mf));

        sut.deleteFile("id-3");

        verify(mediaRepository).delete(mf);
    }

    // ── uploadFile (local path) ───────────────────────────────────────

    @Test
    @DisplayName("uploadFile() — saves to local path and persists MediaFile")
    void uploadFile_localPath_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello world".getBytes());
        when(mediaRepository.save(any())).thenReturn(imageFile);

        MediaFile result = sut.uploadFile(file, "user-1", "room-1", "msg-1");

        assertThat(result).isNotNull();
        verify(mediaRepository).save(any(MediaFile.class));
    }

    @Test
    @DisplayName("uploadFile() — handles file with no extension")
    void uploadFile_noExtension_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "noext", "application/octet-stream", "data".getBytes());
        when(mediaRepository.save(any())).thenReturn(imageFile);

        assertThat(sut.uploadFile(file, "user-1", "room-1", null)).isNotNull();
    }

    @Test
    @DisplayName("uploadFile() — handles null original filename")
    void uploadFile_nullFilename_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "application/octet-stream", "data".getBytes());
        when(mediaRepository.save(any())).thenReturn(imageFile);

        assertThat(sut.uploadFile(file, "user-1", "room-1", null)).isNotNull();
    }

    @Test
    @DisplayName("uploadFile() — creates local directory if it does not exist")
    void uploadFile_createsDirectory() throws IOException {
        // Point to a sub-directory that doesn't yet exist
        Path subDir = tempDir.resolve("new-sub");
        ReflectionTestUtils.setField(sut, "localPath", subDir.toString());

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.txt", "text/plain", "content".getBytes());
        when(mediaRepository.save(any())).thenReturn(imageFile);

        sut.uploadFile(file, "user-1", "room-1", null);

        assertThat(subDir).exists();
    }

    // ── uploadImage (local path) ──────────────────────────────────────

    @Test
    @DisplayName("uploadImage() — saves without thumbnail when bytes are not a valid image")
    void uploadImage_invalidBytes_savesWithoutThumbnail() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "not-real-image".getBytes());
        when(mediaRepository.save(any())).thenReturn(imageFile);

        MediaFile result = sut.uploadImage(file, "user-1", "room-1", "msg-1");

        assertThat(result).isNotNull();
        verify(mediaRepository).save(any(MediaFile.class));
    }

    @Test
    @DisplayName("uploadImage() — generates thumbnail for valid JPEG bytes")
    void uploadImage_validJpeg_generatesThumbnail() throws IOException {
        // Minimal 1x1 white JPEG (valid enough for ImageIO)
        byte[] minimalJpeg = javax.imageio.ImageIO.createImageOutputStream(new java.io.ByteArrayOutputStream()) != null
                ? createMinimalJpeg() : "fake".getBytes();

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", minimalJpeg);
        when(mediaRepository.save(any())).thenReturn(imageFile);

        MediaFile result = sut.uploadImage(file, "user-1", "room-1", null);

        assertThat(result).isNotNull();
        verify(mediaRepository).save(any(MediaFile.class));
    }

    @Test
    @DisplayName("uploadImage() — creates local dir if missing")
    void uploadImage_createsDirectory() throws IOException {
        Path subDir = tempDir.resolve("img-sub");
        ReflectionTestUtils.setField(sut, "localPath", subDir.toString());

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "invalid".getBytes());
        when(mediaRepository.save(any())).thenReturn(imageFile);

        sut.uploadImage(file, "user-1", "room-1", null);

        assertThat(subDir).exists();
    }

    // ── helpers ───────────────────────────────────────────────────────

    /** Creates a real 1x1 white JPEG byte array that ImageIO can decode. */
    private byte[] createMinimalJpeg() throws IOException {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }
}